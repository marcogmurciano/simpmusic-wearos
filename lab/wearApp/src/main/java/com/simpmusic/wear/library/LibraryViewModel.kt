package com.simpmusic.wear.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simpmusic.wear.music.CuentaPlaylist
import com.simpmusic.wear.music.MusicSource
import com.simpmusic.wear.music.WearSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface LibraryUiState {
    data object SinBackup : LibraryUiState
    /** Servidor levantado, esperando que el movil suba el fichero a [direccion]. */
    data class Esperando(val direccion: String) : LibraryUiState
    data object Cargando : LibraryUiState
    /**
     * [deLaCuenta] son las playlists que vienen del servidor de YouTube Music (siempre al
     * dia); [locales] las del backup del movil (una foto del momento).
     */
    data class Listo(
        val locales: List<WearPlaylist>,
        val deLaCuenta: List<CuentaPlaylist> = emptyList(),
    ) : LibraryUiState
    data class Error(val mensaje: String) : LibraryUiState
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val source = MusicSource()
    private val downloads = Downloads(app, source)

    val haySesion: Boolean get() = source.haySesion

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.SinBackup)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _descargadas = MutableStateFlow<List<WearSong>>(emptyList())
    val descargadas: StateFlow<List<WearSong>> = _descargadas.asStateFlow()

    private val _descargando = MutableStateFlow<Set<String>>(emptySet())
    val descargando: StateFlow<Set<String>> = _descargando.asStateFlow()

    init { refrescarDescargas() }

    /**
     * Rutas donde se busca el backup, en orden.
     *
     * Desde Android 10 el almacenamiento con ambito impide leer /sdcard/Download sin
     * permisos ni SAF, asi que la ruta buena es el directorio externo propio de la app,
     * al que se puede enviar el fichero sin conceder nada.
     */
    fun rutasCandidatas(): List<String> {
        val app = getApplication<Application>()
        return listOfNotNull(
            app.getExternalFilesDir(null)?.absolutePath?.plus("/simpmusic-backup.zip"),
            app.filesDir.absolutePath + "/simpmusic-backup.zip",
            "/sdcard/Download/simpmusic-backup.zip",
        )
    }

    /** Importa el backup del movil desde la primera ruta candidata que exista. */
    /**
     * Levanta un servidor web en el reloj para recibir el backup desde el navegador del
     * movil. Es la unica via razonable de meter un fichero en un reloj sin adb ni
     * permisos: la app lo recibe directamente en su propio almacenamiento.
     */
    fun recibirDesdeElMovil() {
        val app = getApplication<Application>()
        val destino = File(app.filesDir, "simpmusic-backup.zip")
        val servidor = ImportServer(destino)
        val direccion = servidor.direccion()
        if (direccion == null) {
            _state.value = LibraryUiState.Error("El reloj no esta en ninguna red wifi")
            return
        }
        _state.value = LibraryUiState.Esperando(direccion)
        viewModelScope.launch {
            servidor.esperarSubida().fold(
                onSuccess = { importar(it.absolutePath) },
                onFailure = { _state.value = LibraryUiState.Error(it.message ?: "Fallo al recibir") },
            )
        }
    }

    fun importar(rutaZip: String? = null) {
        _state.value = LibraryUiState.Cargando
        viewModelScope.launch {
            val app = getApplication<Application>()
            val candidatas = rutaZip?.let { listOf(it) } ?: rutasCandidatas()
            val zip = candidatas.map(::File).firstOrNull { it.exists() && it.canRead() }
            if (zip == null) {
                _state.value = LibraryUiState.Error("Copia el backup a:\n" + candidatas.first())
                return@launch
            }
            val bd = BackupImporter.extraerBaseDeDatos(zip, File(app.cacheDir, "backup.db"))
            if (bd == null) {
                _state.value = LibraryUiState.Error("El ZIP no lleva la base de datos")
                return@launch
            }
            // La cookie viene en el mismo ZIP: con ella el reloj deja de depender de
            // ficheros y pide la biblioteca al servidor, como Spotify o YT Music.
            SessionImporter.extraerCookie(zip)?.let(source::iniciarSesion)

            val locales = BackupImporter.leerPlaylists(bd).getOrElse { emptyList() }
            val deLaCuenta =
                if (source.haySesion) source.playlistsDeLaCuenta().getOrElse { emptyList() }
                else emptyList()

            _state.value =
                if (locales.isEmpty() && deLaCuenta.isEmpty()) {
                    LibraryUiState.Error("Sin playlists")
                } else {
                    LibraryUiState.Listo(locales = locales, deLaCuenta = deLaCuenta)
                }
        }
    }

    fun descargar(song: WearSong) {
        if (downloads.estaDescargada(song.videoId)) return
        _descargando.value = _descargando.value + song.videoId
        viewModelScope.launch {
            downloads.descargar(song)
            _descargando.value = _descargando.value - song.videoId
            refrescarDescargas()
        }
    }

    fun descargarTodas(playlist: WearPlaylist) = playlist.songs.forEach(::descargar)

    /** Canciones de una playlist de la cuenta, pedidas al servidor. */
    private val _cancionesCuenta = MutableStateFlow<List<WearSong>>(emptyList())
    val cancionesCuenta: StateFlow<List<WearSong>> = _cancionesCuenta.asStateFlow()

    fun abrirPlaylistDeCuenta(playlist: CuentaPlaylist) {
        _cancionesCuenta.value = emptyList()
        viewModelScope.launch {
            _cancionesCuenta.value = source.cancionesDe(playlist.browseId).getOrElse { emptyList() }
        }
    }

    /** Ruta local reproducible sin red, o null si no esta descargada. */
    fun uriOffline(videoId: String): String? =
        downloads.ficheroDe(videoId).takeIf { downloads.estaDescargada(videoId) }?.toURI()?.toString()

    private fun refrescarDescargas() { _descargadas.value = downloads.descargadas() }
}
