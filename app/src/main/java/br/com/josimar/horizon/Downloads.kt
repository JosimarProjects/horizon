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

/** Estado compartilhado + organização dos arquivos em pastas. */
object Downloads {
    val progress = MutableStateFlow<DownloadProgress?>(null)

    const val ROOT_LABEL = "Principal"
    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "m4a", "mp3", "mov")

    enum class Sort(val label: String) {
        RECENT("Mais recentes"),
        OLDEST("Mais antigos"),
        NAME_ASC("Nome (A-Z)"),
        NAME_DESC("Nome (Z-A)"),
        SIZE_DESC("Maiores"),
        SIZE_ASC("Menores")
    }

    /** Pasta-raiz do app (específica, não precisa de permissão). */
    fun outputDir(ctx: Context): File {
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Horizon")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Pasta de destino: vazio = raiz (Principal); senão subpasta. */
    fun folderDir(ctx: Context, folder: String?): File {
        val base = outputDir(ctx)
        val d = if (folder.isNullOrBlank()) base else File(base, sanitize(folder))
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Lista as pastas (subdiretórios) criadas, em ordem alfabética. */
    fun folders(ctx: Context): List<String> =
        outputDir(ctx).listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    fun createFolder(ctx: Context, name: String): Boolean {
        val n = sanitize(name)
        if (n.isBlank()) return false
        return File(outputDir(ctx), n).mkdirs()
    }

    /**
     * Exclui uma pasta. Se moveContentsToRoot, move os vídeos para a raiz
     * (Principal) antes; senão apaga tudo que estiver dentro.
     */
    fun deleteFolder(ctx: Context, name: String, moveContentsToRoot: Boolean): Boolean {
        val n = sanitize(name)
        if (n.isBlank()) return false
        val dir = File(outputDir(ctx), n)
        if (!dir.isDirectory) return false
        if (moveContentsToRoot) {
            dir.listFiles()?.forEach { if (it.isFile) moveToFolder(ctx, it, "") }
        }
        return dir.deleteRecursively()
    }

    /**
     * Lista vídeos. folder == null → todos (recursivo); "" → só a raiz
     * (Principal, sem subpastas); nome → aquela pasta.
     */
    fun listVideos(ctx: Context, folder: String? = null, sort: Sort = Sort.RECENT): List<File> {
        val base = outputDir(ctx)
        val seq = when {
            folder == null -> base.walkTopDown().filter { it.isFile }
            folder.isBlank() -> base.listFiles()?.asSequence()?.filter { it.isFile } ?: emptySequence()
            else -> File(base, sanitize(folder)).walkTopDown().filter { it.isFile }
        }
        val files = seq.filter { it.extension.lowercase() in VIDEO_EXTS }.toList()
        return when (sort) {
            Sort.RECENT -> files.sortedByDescending { it.lastModified() }
            Sort.OLDEST -> files.sortedBy { it.lastModified() }
            Sort.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            Sort.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            Sort.SIZE_DESC -> files.sortedByDescending { it.length() }
            Sort.SIZE_ASC -> files.sortedBy { it.length() }
        }
    }

    /** Renomeia o arquivo mantendo a extensão. Retorna o novo arquivo ou null. */
    fun renameFile(file: File, newName: String): File? {
        val base = sanitize(newName).ifBlank { return null }
        val ext = file.extension
        val finalName = if (ext.isNotBlank() && !base.endsWith(".$ext")) "$base.$ext" else base
        val target = File(file.parentFile, finalName)
        if (target.absolutePath == file.absolutePath) return file
        if (target.exists()) return null
        return if (file.renameTo(target)) target else null
    }

    /** Move um arquivo já baixado para outra pasta (vazio = raiz). */
    fun moveToFolder(ctx: Context, file: File, folder: String?): Boolean {
        val dest = folderDir(ctx, folder)
        val target = File(dest, file.name)
        if (file.absolutePath == target.absolutePath) return true
        if (file.renameTo(target)) return true
        // fallback: copia + apaga (ex.: entre volumes)
        return try {
            file.copyTo(target, overwrite = true)
            file.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").take(60)
}
