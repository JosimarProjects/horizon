package br.com.josimar.horizon

import android.content.Context

/** Guarda a posição de reprodução por arquivo, para "Continuar assistindo". */
object Resume {
    private const val PREFS = "resume"
    private const val HIST = "history"

    /** Marca que o arquivo foi aberto agora (para o histórico). */
    fun markPlayed(ctx: Context, path: String) {
        ctx.getSharedPreferences(HIST, Context.MODE_PRIVATE)
            .edit().putLong(path, System.currentTimeMillis()).apply()
    }

    /** Caminhos assistidos, do mais recente para o mais antigo. */
    fun history(ctx: Context): List<String> =
        ctx.getSharedPreferences(HIST, Context.MODE_PRIVATE).all.entries
            .sortedByDescending { (it.value as? Long) ?: 0L }
            .map { it.key }

    fun clearHistory(ctx: Context) {
        ctx.getSharedPreferences(HIST, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun save(ctx: Context, path: String, positionMs: Long, durationMs: Long) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // perto do fim ou muito no início → não marca como "continuar"
        if (durationMs > 0 && (positionMs < 3000 || positionMs > durationMs - 5000)) {
            p.edit().remove(path).remove("$path:dur").apply()
        } else {
            p.edit().putLong(path, positionMs).putLong("$path:dur", durationMs).apply()
        }
    }

    fun position(ctx: Context, path: String): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(path, 0L)

    /** Fração assistida 0..1 (para a barrinha de progresso). */
    fun fraction(ctx: Context, path: String): Float {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pos = p.getLong(path, 0L)
        val dur = p.getLong("$path:dur", 0L)
        return if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    }

    fun hasResume(ctx: Context, path: String): Boolean = position(ctx, path) > 0L

    // ---- assistido (flag manual; auto quando >= 95%) ----
    private const val WATCHED = "watched"

    fun isWatched(ctx: Context, path: String): Boolean {
        val p = ctx.getSharedPreferences(WATCHED, Context.MODE_PRIVATE)
        if (p.contains(path)) return p.getBoolean(path, false)
        return fraction(ctx, path) >= 0.95f
    }

    fun setWatched(ctx: Context, path: String, watched: Boolean) {
        ctx.getSharedPreferences(WATCHED, Context.MODE_PRIVATE)
            .edit().putBoolean(path, watched).apply()
    }
}
