package com.simpmusic.wear.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simpmusic.wear.music.WearSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface LibraryUiState {
    data object SinBackup : LibraryUiState
    data object Cargando : LibraryUiState
    data class Listo(val playlists: List<WearPlaylist>) : LibraryUiState
    data class Error(val mensaje: String) : LibraryUiState
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val downloads = Downloads(app)

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
            _state.value = BackupImporter.leerPlaylists(bd).fold(
                onSuccess = { if (it.isEmpty()) LibraryUiState.Error("Sin playlists") else LibraryUiState.Listo(it) },
                onFailure = { LibraryUiState.Error(it.message ?: "Backup ilegible") },
            )
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

    /** Ruta local reproducible sin red, o null si no esta descargada. */
    fun uriOffline(videoId: String): String? =
        downloads.ficheroDe(videoId).takeIf { downloads.estaDescargada(videoId) }?.toURI()?.toString()

    private fun refrescarDescargas() { _descargadas.value = downloads.descargadas() }
}
