package com.simpmusic.wear.library

import android.content.Context
import android.util.Log
import com.simpmusic.wear.music.MusicSource
import com.simpmusic.wear.music.WearSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descargas para escuchar sin cobertura.
 *
 * Se guarda el audio como fichero suelto en vez de usar la cache de ExoPlayer a proposito:
 * las URL de stream de YouTube caducan en unas horas, asi que cachear por URL no sirve para
 * offline. Un fichero por videoId es trivialmente verificable (modo avion) y no depende de
 * que la URL siga viva.
 */
class Downloads(context: Context, private val source: MusicSource = MusicSource()) {

    private companion object { const val TAG = "WearDownloads" }

    private val dir = File(context.filesDir, "descargas").apply { mkdirs() }
    private val indice = File(dir, "indice.json")

    fun ficheroDe(videoId: String) = File(dir, "$videoId.opus")

    fun estaDescargada(videoId: String) = ficheroDe(videoId).let { it.exists() && it.length() > 0 }

    suspend fun descargar(song: WearSong): Result<File> = withContext(Dispatchers.IO) {
        val destino = ficheroDe(song.videoId)
        if (estaDescargada(song.videoId)) return@withContext Result.success(destino)

        source.streamUrl(song.videoId).mapCatching { url ->
            val parcial = File(dir, "${song.videoId}.parcial")
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }.use { conexion ->
                require(conexion.responseCode in 200..299) { "HTTP ${conexion.responseCode}" }
                conexion.inputStream.use { entrada ->
                    parcial.outputStream().buffered().use { entrada.copyTo(it) }
                }
            }
            // Renombrar al final: si se corta la descarga no queda un fichero a medias
            // que luego parezca valido.
            require(parcial.renameTo(destino)) { "no se pudo renombrar ${parcial.name}" }
            registrar(song)
            Log.i(TAG, "descargada ${song.videoId}: ${destino.length()} bytes")
            destino
        }.onFailure {
            File(dir, "${song.videoId}.parcial").delete()
            Log.e(TAG, "fallo al descargar ${song.videoId}", it)
        }
    }

    fun descargadas(): List<WearSong> =
        runCatching {
            if (!indice.exists()) return emptyList()
            val array = JSONArray(indice.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val id = o.getString("videoId")
                if (!estaDescargada(id)) null
                else WearSong(id, o.getString("title"), o.getString("artist"), o.optString("thumbnail"))
            }
        }.getOrElse {
            Log.e(TAG, "indice ilegible", it); emptyList()
        }

    private fun registrar(song: WearSong) {
        val actuales = descargadas().filterNot { it.videoId == song.videoId } + song
        val array = JSONArray()
        actuales.forEach {
            array.put(
                JSONObject()
                    .put("videoId", it.videoId).put("title", it.title)
                    .put("artist", it.artist).put("thumbnail", it.thumbnail),
            )
        }
        indice.writeText(array.toString())
    }
}

private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
    try { block(this) } finally { disconnect() }
