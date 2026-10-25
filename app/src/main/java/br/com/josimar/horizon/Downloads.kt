package br.com.josimar.horizon

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Estado de um download em andamento, observado pela Home. */
data class DownloadProgress(
    val url: String,
    val percent: Float = 0f,
    val line: String = "",
    val done: Boolean = false,
    val error: String? = null,
    val file: String? = null,
    val index: Int = 0,   // posição na fila (1..total)
    val total: Int = 0    // quantos itens na fila
)

/** Estado compartilhado entre o serviço de download e a UI. */
object Downloads {
    val progress = MutableStateFlow<DownloadProgress?>(null)

    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "m4a", "mp3", "mov")

    /** Pasta de saída (específica do app, não precisa de permissão). */
    fun outputDir(ctx: Context): File {
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Horizon")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Lista os vídeos já baixados (inclui subpastas de playlist),
     * mais recentes primeiro.
     */
    fun listVideos(ctx: Context): List<File> =
        outputDir(ctx).walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in VIDEO_EXTS }
            .sortedByDescending { it.lastModified() }
            .toList()
}
