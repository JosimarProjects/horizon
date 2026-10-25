package br.com.josimar.horizon

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

/**
 * Servidor HTTP local que serve UM arquivo para o Chromecast buscar na rede.
 * Suporta Range (necessário para seek no Cast).
 */
class LocalMediaServer(private val file: File, port: Int = 8099) : NanoHTTPD(port) {

    val mime: String = when (file.extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        else -> "video/mp4"
    }

    override fun serve(session: IHTTPSession): Response {
        val total = file.length()
        val range = session.headers["range"]
        return try {
            if (range != null && range.startsWith("bytes=")) {
                val parts = range.removePrefix("bytes=").split("-")
                val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val end = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it in start until total } ?: (total - 1)
                val length = end - start + 1
                val fis = FileInputStream(file).apply { skip(start) }
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, fis, length).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes $start-$end/$total")
                }
            } else {
                newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(file), total).apply {
                    addHeader("Accept-Ranges", "bytes")
                }
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "erro")
        }
    }
}
