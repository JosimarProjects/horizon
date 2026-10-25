package br.com.josimar.horizon

/** Cores por pasta — cada pasta ganha uma cor estável a partir do nome. */
object Palette {
    const val ACCENT = 0xFFFF9800.toInt() // laranja (Principal)

    private val colors = intArrayOf(
        0xFF4CAF50.toInt(), // verde
        0xFF2196F3.toInt(), // azul
        0xFFE91E63.toInt(), // rosa
        0xFF9C27B0.toInt(), // roxo
        0xFF00BCD4.toInt(), // ciano
        0xFFFFC107.toInt(), // âmbar
        0xFFFF5722.toInt(), // laranja escuro
        0xFF8BC34A.toInt(), // verde claro
        0xFF3F51B5.toInt(), // índigo
        0xFF009688.toInt()  // teal
    )

    /** null/"" (raiz = Principal) usa o laranja; demais pastas uma cor fixa. */
    fun colorFor(folder: String?): Int {
        if (folder.isNullOrBlank()) return ACCENT
        val idx = (folder.hashCode() and 0x7fffffff) % colors.size
        return colors[idx]
    }
}
