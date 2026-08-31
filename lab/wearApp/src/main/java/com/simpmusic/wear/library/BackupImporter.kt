package com.simpmusic.wear.library

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.simpmusic.wear.music.WearSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.zip.ZipInputStream

data class WearPlaylist(
    val id: Long,
    val title: String,
    val songs: List<WearSong>,
)

/**
 * Lee las playlists locales de un backup de SimpMusic.
 *
 * El backup que genera la app de movil (AutoBackupWorker) es un ZIP que contiene la base
 * de datos Room entera con el nombre "Music Database". Aqui se extrae y se consulta con
 * SQLite a pelo: NO se usa Room ni el modulo :data, porque eso arrastraria toda la
 * inyeccion de dependencias del core (Spotify, IA, letras) por leer tres tablas.
 *
 * Consecuencia deliberada: el movil no se toca. Se exporta con el SimpMusic oficial y el
 * reloj lee el fichero.
 */
object BackupImporter {

    private const val TAG = "WearImport"
    private const val NOMBRE_BD_EN_ZIP = "Music Database"

    /**
     * Extrae la base de datos del ZIP a [destino]. Devuelve null ante cualquier problema
     * (fichero ilegible, ZIP corrupto, permisos): nunca lanza, porque esto se invoca desde
     * la UI y una excepcion aqui tumbaba la app.
     */
    fun extraerBaseDeDatos(zip: File, destino: File): File? = runCatching {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entrada = zis.nextEntry
            while (entrada != null) {
                if (entrada.name == NOMBRE_BD_EN_ZIP) {
                    destino.outputStream().buffered().use { zis.copyTo(it) }
                    Log.i(TAG, "base de datos extraida: ${destino.length()} bytes")
                    return@runCatching destino
                }
                entrada = zis.nextEntry
            }
        }
        Log.e(TAG, "el ZIP no contiene '$NOMBRE_BD_EN_ZIP'")
        null
    }.getOrElse {
        Log.e(TAG, "no se pudo abrir el backup ${zip.absolutePath}", it)
        null
    }

    /** Lee las playlists locales y sus canciones. Solo lectura, no modifica el backup. */
    suspend fun leerPlaylists(bd: File): Result<List<WearPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            SQLiteDatabase.openDatabase(bd.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val playlists = mutableListOf<WearPlaylist>()
                db.rawQuery("SELECT id, title FROM local_playlist ORDER BY title", null).use { c ->
                    while (c.moveToNext()) {
                        playlists += WearPlaylist(
                            id = c.getLong(0),
                            title = c.getString(1) ?: "Sin titulo",
                            songs = emptyList(),
                        )
                    }
                }
                playlists.map { it.copy(songs = leerCanciones(db, it.id)) }
                    .also { Log.i(TAG, "leidas ${it.size} playlists") }
            }
        }.onFailure { Log.e(TAG, "no se pudo leer el backup", it) }
    }

    private fun leerCanciones(db: SQLiteDatabase, playlistId: Long): List<WearSong> {
        val sql = """
            SELECT s.videoId, s.title, s.artistName, s.thumbnails
            FROM pair_song_local_playlist p
            JOIN song s ON s.videoId = p.songId
            WHERE p.playlistId = ?
            ORDER BY p.position
        """.trimIndent()
        val canciones = mutableListOf<WearSong>()
        db.rawQuery(sql, arrayOf(playlistId.toString())).use { c ->
            while (c.moveToNext()) {
                canciones += WearSong(
                    videoId = c.getString(0),
                    title = c.getString(1) ?: "",
                    artist = artistasDesdeJson(c.getString(2)),
                    thumbnail = c.getString(3).orEmpty(),
                )
            }
        }
        return canciones
    }

    /** `artistName` se guarda como array JSON (ver Converters.fromArrayList del core). */
    private fun artistasDesdeJson(valor: String?): String {
        if (valor.isNullOrBlank()) return "Desconocido"
        return runCatching {
            val array = JSONArray(valor)
            (0 until array.length()).joinToString(", ") { array.getString(it) }
        }.getOrElse { valor }.ifBlank { "Desconocido" }
    }
}
