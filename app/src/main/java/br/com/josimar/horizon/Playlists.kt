package br.com.josimar.horizon

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Listas personalizadas (playlists): nome -> caminhos ordenados. */
object Playlists {
    private const val PREFS = "playlists"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Nomes das listas (só as que ainda têm algum vídeo presente). */
    fun names(ctx: Context): List<String> =
        prefs(ctx).all.keys
            .filter { items(ctx, it).isNotEmpty() }
            .sorted()

    /** Todos os nomes, inclusive listas vazias (para o seletor "adicionar à lista"). */
    fun allNames(ctx: Context): List<String> = prefs(ctx).all.keys.sorted()

    fun create(ctx: Context, name: String) {
        val n = name.trim()
        if (n.isBlank() || prefs(ctx).contains(n)) return
        prefs(ctx).edit().putString(n, "[]").apply()
    }

    fun delete(ctx: Context, name: String) = prefs(ctx).edit().remove(name).apply()

    fun rename(ctx: Context, old: String, new: String) {
        val n = new.trim()
        if (n.isBlank() || old == n || prefs(ctx).contains(n)) return
        val raw = prefs(ctx).getString(old, "[]")
        prefs(ctx).edit().remove(old).putString(n, raw).apply()
    }

    /** Vídeos da lista, na ordem, só os que existem. */
    fun items(ctx: Context, name: String): List<File> {
        val raw = prefs(ctx).getString(name, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length())
            .map { File(arr.optString(it)) }
            .filter { it.exists() }
    }

    private fun paths(ctx: Context, name: String): MutableList<String> {
        val raw = prefs(ctx).getString(name, "[]")
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        return (0 until arr.length()).map { arr.optString(it) }.toMutableList()
    }

    private fun savePaths(ctx: Context, name: String, list: List<String>) {
        prefs(ctx).edit().putString(name, JSONArray(list).toString()).apply()
    }

    fun add(ctx: Context, name: String, newPaths: List<String>) {
        val list = paths(ctx, name)
        newPaths.forEach { if (it !in list) list.add(it) }
        savePaths(ctx, name, list)
    }

    fun removeItem(ctx: Context, name: String, path: String) {
        val list = paths(ctx, name)
        list.remove(path)
        savePaths(ctx, name, list)
    }
}
