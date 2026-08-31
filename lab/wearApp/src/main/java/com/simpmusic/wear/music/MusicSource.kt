package com.simpmusic.wear.music

import android.util.Log
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.kotlinytmusicscraper.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una playlist de tu cuenta de YouTube Music (vive en el servidor, no en el reloj). */
data class CuentaPlaylist(
    val browseId: String,
    val title: String,
    val author: String,
)

/** Una cancion, con lo minimo que necesita el reloj. */
data class WearSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
)

/**
 * Acceso a YouTube Music desde el reloj.
 *
 * Usa directamente `YouTube` del modulo :kotlinYtmusicScraper. No pasa por el Koin de
 * SimpMusic a proposito: ese arrastra Room, DataStore, Spotify, AI y letras, que en un
 * reloj de 2 GB no pintan nada (ADR 0004). `YouTube()` no tiene dependencias inyectadas
 * y la busqueda funciona sin login.
 */
class MusicSource(private val youtube: YouTube = YouTube()) {

    private companion object { const val TAG = "WearMusic" }

    /** Hay sesion de YouTube Music (importada del backup del movil). */
    val haySesion: Boolean get() = !youtube.cookie.isNullOrBlank()

    /**
     * Inicia sesion con la cookie del backup. A partir de aqui el reloj puede pedir la
     * biblioteca de la cuenta al servidor, sin depender de mas ficheros.
     */
    fun iniciarSesion(cookie: String) {
        youtube.cookie = cookie
        Log.i(TAG, "sesion establecida")
    }

    /**
     * Playlists de tu cuenta de YouTube Music, pedidas al servidor.
     *
     * Se navega el BrowseResponse a mano porque el parser del core es `internal` a :data
     * y ese modulo arrastra toda la inyeccion de dependencias.
     */
    suspend fun playlistsDeLaCuenta(): Result<List<CuentaPlaylist>> = withContext(Dispatchers.IO) {
        youtube.getLibraryPlaylists().mapCatching { data ->
            val items = data.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs?.firstOrNull()
                ?.tabRenderer?.content
                ?.sectionListRenderer?.contents?.firstOrNull()
                ?.gridRenderer?.items
                .orEmpty()

            items.mapNotNull { item ->
                val r = item.musicTwoRowItemRenderer ?: return@mapNotNull null
                val browseId = r.navigationEndpoint?.browseEndpoint?.browseId ?: return@mapNotNull null
                CuentaPlaylist(
                    browseId = browseId,
                    title = r.title?.runs?.firstOrNull()?.text ?: "Sin titulo",
                    author = r.subtitle?.runs?.firstOrNull()?.text.orEmpty(),
                )
            }
        }.onSuccess { Log.i(TAG, "playlists de la cuenta: ${it.size}") }
            .onFailure { Log.e(TAG, "no se pudieron leer las playlists de la cuenta", it) }
    }

    /** Canciones de una playlist de la cuenta. */
    suspend fun cancionesDe(browseId: String): Result<List<WearSong>> = withContext(Dispatchers.IO) {
        youtube.getPlaylistFullTracks(browseId)
            .map { canciones -> canciones.map { it.toWearSong() } }
            .onFailure { Log.e(TAG, "no se pudieron leer las canciones de $browseId", it) }
    }

    suspend fun search(query: String): Result<List<WearSong>> = withContext(Dispatchers.IO) {
        youtube.search(query, YouTube.SearchFilter.FILTER_SONG).map { result ->
            result.items.filterIsInstance<SongItem>().map { it.toWearSong() }
        }.onSuccess { lista ->
            Log.i(TAG, "resultados: " + lista.joinToString(" | ") { "${it.videoId}=${it.title}" })
        }.onFailure { Log.e(TAG, "busqueda fallida: $query", it) }
    }

    /**
     * Resuelve la URL de audio reproducible de una cancion.
     *
     * Se pide el mejor formato de solo audio disponible. Los itag 251/250 son Opus y
     * el 141 AAC; en un reloj con auriculares Bluetooth la diferencia es inaudible, asi
     * que se coge el de menor bitrate suficiente para gastar menos bateria y datos.
     */
    suspend fun streamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        youtube.player(videoId).mapCatching { (_, response, _) ->
            val audio = response.streamingData?.adaptiveFormats
                ?.filter { it.isAudio && it.url != null }
                ?: emptyList()
            require(audio.isNotEmpty()) { "sin formatos de audio para $videoId" }

            val chosen = audio.firstOrNull { it.itag == 251 }
                ?: audio.firstOrNull { it.itag == 250 }
                ?: audio.firstOrNull { it.itag == 141 }
                ?: audio.minByOrNull { it.bitrate }!!

            Log.i(TAG, "stream $videoId itag=${chosen.itag} bitrate=${chosen.bitrate}")
            chosen.url!!
        }.onFailure { Log.e(TAG, "no se pudo resolver el stream de $videoId", it) }
    }
}

private fun SongItem.toWearSong() = WearSong(
    videoId = id,
    title = title,
    artist = artists.joinToString(", ") { it.name }.ifBlank { "Desconocido" },
    thumbnail = thumbnail,
)
