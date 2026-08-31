package com.simpmusic.wear.library

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket

/**
 * Servidor web minimo para recibir el backup desde el movil.
 *
 * Es el unico modo razonable de meter un fichero en un reloj: no hay gestor de ficheros
 * util, el almacenamiento con ambito bloquea /sdcard/Download, el selector de documentos
 * no existe en Wear, y `adb` solo vale en compilaciones de depuracion. Levantando el
 * servidor aqui, la app recibe el fichero DIRECTAMENTE en su propio almacenamiento, sin
 * pedir ni un permiso. Es el mismo truco que usan las herramientas de sideload de Wear.
 *
 * Acepta una sola subida y se apaga: no tiene sentido dejar un puerto abierto en un reloj.
 */
class ImportServer(private val destino: File) {

    private companion object {
        const val TAG = "WearImportServer"
        const val PUERTO = 8080
        const val MAX_BYTES = 64L * 1024 * 1024
    }

    /** IP del reloj en la red local, para poder decirsela al usuario. */
    fun direccion(): String? =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
                ?.let { "$it:$PUERTO" }
        }.getOrNull()

    /**
     * Espera UNA subida y devuelve el fichero recibido. Bloquea hasta que llegue o falle,
     * asi que se llama desde un contexto de IO.
     */
    suspend fun esperarSubida(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            ServerSocket(PUERTO).use { servidor ->
                Log.i(TAG, "esperando subida en ${direccion()}")
                var recibido: File? = null
                while (recibido == null) {
                    servidor.accept().use { cliente ->
                        val entrada = BufferedInputStream(cliente.getInputStream())
                        val salida = cliente.getOutputStream()
                        val peticion = leerLinea(entrada)
                        val cabeceras = leerCabeceras(entrada)
                        Log.i(TAG, "peticion: $peticion")

                        if (peticion.startsWith("POST")) {
                            recibido = recibirFichero(entrada, cabeceras)
                            responder(salida, "Recibido. Vuelve al reloj.")
                        } else {
                            responder(salida, FORMULARIO, "text/html")
                        }
                    }
                }
                recibido
            }
        }.onFailure { Log.e(TAG, "fallo del servidor de importacion", it) }
    }

    private fun recibirFichero(entrada: BufferedInputStream, cabeceras: Map<String, String>): File {
        val longitud = cabeceras["content-length"]?.toLongOrNull()
            ?: error("el navegador no indico el tamano")
        require(longitud in 1..MAX_BYTES) { "tamano fuera de rango: $longitud" }

        // El navegador envia multipart/form-data. En vez de un parser completo, se busca
        // la cabecera EBML/ZIP: el cuerpo del fichero empieza tras la linea en blanco que
        // sigue a las cabeceras de la parte.
        val cuerpo = ByteArray(longitud.toInt())
        var leidos = 0
        while (leidos < cuerpo.size) {
            val n = entrada.read(cuerpo, leidos, cuerpo.size - leidos)
            if (n <= 0) break
            leidos += n
        }

        val inicio = indiceDe(cuerpo, "PK".toByteArray())
        require(inicio >= 0) { "el fichero subido no es un ZIP" }
        val fin = ultimoIndiceDe(cuerpo, byteArrayOf(0x50, 0x4B, 0x05, 0x06))
        val corte = if (fin > inicio) minOf(fin + 22, cuerpo.size) else cuerpo.size

        destino.outputStream().use { it.write(cuerpo, inicio, corte - inicio) }
        Log.i(TAG, "recibido ${destino.length()} bytes en ${destino.name}")
        return destino
    }

    private fun leerLinea(entrada: BufferedInputStream): String {
        val sb = StringBuilder()
        while (true) {
            val c = entrada.read()
            if (c == -1 || c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun leerCabeceras(entrada: BufferedInputStream): Map<String, String> {
        val mapa = mutableMapOf<String, String>()
        while (true) {
            val linea = leerLinea(entrada)
            if (linea.isBlank()) break
            val i = linea.indexOf(':')
            if (i > 0) mapa[linea.take(i).trim().lowercase()] = linea.drop(i + 1).trim()
        }
        return mapa
    }

    private fun responder(salida: java.io.OutputStream, cuerpo: String, tipo: String = "text/plain") {
        val bytes = cuerpo.toByteArray()
        salida.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: $tipo; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        salida.write(bytes)
        salida.flush()
    }

    private fun indiceDe(datos: ByteArray, patron: ByteArray): Int {
        outer@ for (i in 0..datos.size - patron.size) {
            for (j in patron.indices) if (datos[i + j] != patron[j]) continue@outer
            return i
        }
        return -1
    }

    private fun ultimoIndiceDe(datos: ByteArray, patron: ByteArray): Int {
        outer@ for (i in datos.size - patron.size downTo 0) {
            for (j in patron.indices) if (datos[i + j] != patron[j]) continue@outer
            return i
        }
        return -1
    }
}

private val FORMULARIO = """
<!doctype html><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Enviar backup al reloj</title>
<style>
 body{font-family:system-ui,sans-serif;margin:0;padding:2rem;background:#111;color:#eee}
 h1{font-size:1.3rem} .caja{max-width:32rem;margin:auto}
 input[type=file]{width:100%;padding:1rem;background:#222;border:1px dashed #666;border-radius:.5rem;color:#eee}
 button{margin-top:1rem;width:100%;padding:1rem;font-size:1rem;border:0;border-radius:.5rem;background:#d0bcff;color:#381e72;font-weight:600}
 p{color:#aaa;line-height:1.5}
</style>
<div class=caja>
<h1>Enviar backup al reloj</h1>
<p>Elige el fichero de backup que exportaste desde SimpMusic en el movil.
Trae tus playlists y tu sesion de YouTube Music.</p>
<form method=post enctype=multipart/form-data>
  <input type=file name=backup accept=".zip" required>
  <button type=submit>Enviar al reloj</button>
</form>
</div>
""".trimIndent()
