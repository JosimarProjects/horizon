package br.com.josimar.horizon

import android.content.Context
import java.io.File

/**
 * Categorias (tags) por vídeo e capas por pasta/categoria.
 * Independente das pastas: um vídeo pode estar na pasta X e ter a categoria Y.
 */
object Catalog {
    private const val CATS = "categories"   // path -> categoria
    private const val COVERS = "covers"     // chave (pasta|cat) -> path da capa

    // ---- categorias (tags) ----

    fun setCategory(ctx: Context, path: String, category: String?) {
        val p = ctx.getSharedPreferences(CATS, Context.MODE_PRIVATE)
        if (category.isNullOrBlank()) p.edit().remove(path).apply()
        else p.edit().putString(path, category.trim()).apply()
    }

    fun category(ctx: Context, path: String): String? =
        ctx.getSharedPreferences(CATS, Context.MODE_PRIVATE).getString(path, null)

    /** Lista de categorias existentes (só as que têm arquivos presentes). */
    fun categories(ctx: Context): List<String> {
        val p = ctx.getSharedPreferences(CATS, Context.MODE_PRIVATE)
        return p.all.entries
            .filter { File(it.key).exists() }
            .mapNotNull { it.value as? String }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    fun inCategory(ctx: Context, category: String): List<File> {
        val p = ctx.getSharedPreferences(CATS, Context.MODE_PRIVATE)
        return p.all.entries
            .filter { (it.value as? String) == category }
            .map { File(it.key) }
            .filter { it.exists() }
            .sortedByDescending { it.lastModified() }
    }

    // ---- capas ----

    fun setCover(ctx: Context, key: String, path: String) {
        ctx.getSharedPreferences(COVERS, Context.MODE_PRIVATE).edit().putString(key, path).apply()
    }

    fun cover(ctx: Context, key: String): File? {
        val path = ctx.getSharedPreferences(COVERS, Context.MODE_PRIVATE).getString(key, null)
        return path?.let { File(it) }?.takeIf { it.exists() }
    }
}
