package com.simpmusic.wear.library

import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Extrae la cookie de sesion de YouTube Music del backup del movil.
 *
 * SimpMusic guarda la cookie en DataStore, y AutoBackupWorker mete ese fichero
 * ("<nombre>.preferences_pb") dentro del ZIP. Con la cookie el reloj deja de depender de
 * ficheros: pide las playlists directamente al servidor, como hacen Spotify o YT Music.
 *
 * El formato es protobuf de DataStore Preferences:
 *   PreferenceMap { map<string, Value> preferences }  (campo 1)
 *   entrada       { string key = 1; Value value = 2 }
 *   Value         { oneof { ... string string = 5 ... } }
 * Se parsea a mano en vez de traer androidx.datastore solo para leer una clave.
 */
object SessionImporter {

    private const val TAG = "WearSession"
    private const val CLAVE_COOKIE = "cookie"

    fun extraerCookie(zip: File): String? = runCatching {
        val pb = leerEntradaPreferencias(zip) ?: return@runCatching null
        buscarCadena(pb, CLAVE_COOKIE)?.also {
            Log.i(TAG, "cookie encontrada (${it.length} caracteres)")
        }
    }.getOrElse {
        Log.e(TAG, "no se pudo leer la sesion del backup", it)
        null
    }

    private fun leerEntradaPreferencias(zip: File): ByteArray? {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (e.name.endsWith(".preferences_pb")) return zis.readBytes()
                e = zis.nextEntry
            }
        }
        Log.w(TAG, "el backup no lleva preferencias: no hay sesion que importar")
        return null
    }

    /**
     * Recorre el protobuf buscando la entrada del mapa cuya clave sea [clave] y devuelve su
     * valor de tipo cadena. Solo se navegan los campos necesarios; el resto se salta.
     */
    private fun buscarCadena(datos: ByteArray, clave: String): String? {
        val lector = Lector(datos)
        while (lector.quedan()) {
            val (campo, tipo) = lector.etiqueta()
            if (campo == 1 && tipo == 2) {
                val entrada = lector.bytes()
                descomponerEntrada(entrada)?.let { (k, v) -> if (k == clave) return v }
            } else {
                lector.saltar(tipo)
            }
        }
        return null
    }

    private fun descomponerEntrada(entrada: ByteArray): Pair<String, String>? {
        val lector = Lector(entrada)
        var clave: String? = null
        var valor: String? = null
        while (lector.quedan()) {
            val (campo, tipo) = lector.etiqueta()
            when {
                campo == 1 && tipo == 2 -> clave = String(lector.bytes())
                campo == 2 && tipo == 2 -> valor = extraerCadenaDeValue(lector.bytes())
                else -> lector.saltar(tipo)
            }
        }
        return if (clave != null && valor != null) clave to valor else null
    }

    /** En Value, la cadena es el campo 5. Otros tipos (bool, int...) no interesan aqui. */
    private fun extraerCadenaDeValue(value: ByteArray): String? {
        val lector = Lector(value)
        while (lector.quedan()) {
            val (campo, tipo) = lector.etiqueta()
            if (campo == 5 && tipo == 2) return String(lector.bytes())
            lector.saltar(tipo)
        }
        return null
    }

    /** Lector minimo de protobuf: solo varint, longitud-delimitado y saltos. */
    private class Lector(private val datos: ByteArray) {
        private var i = 0
        fun quedan() = i < datos.size

        fun varint(): Long {
            var resultado = 0L
            var desplazamiento = 0
            while (i < datos.size) {
                val b = datos[i++].toInt()
                resultado = resultado or ((b and 0x7F).toLong() shl desplazamiento)
                if (b and 0x80 == 0) break
                desplazamiento += 7
            }
            return resultado
        }

        fun etiqueta(): Pair<Int, Int> {
            val t = varint()
            return (t ushr 3).toInt() to (t and 0x7).toInt()
        }

        fun bytes(): ByteArray {
            val n = varint().toInt()
            val fin = minOf(i + n, datos.size)
            return datos.copyOfRange(i, fin).also { i = fin }
        }

        fun saltar(tipo: Int) {
            when (tipo) {
                0 -> varint()
                1 -> i += 8
                2 -> bytes()
                5 -> i += 4
                else -> i = datos.size   // tipo desconocido: abortar en vez de leer basura
            }
        }
    }
}
