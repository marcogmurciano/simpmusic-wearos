package com.simpmusic.wear.music

import android.util.Log
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.kotlinytmusicscraper.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
