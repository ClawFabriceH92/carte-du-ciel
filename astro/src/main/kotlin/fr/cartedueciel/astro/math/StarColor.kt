package fr.cartedueciel.astro.math

/**
 * Couleur approximative d'une étoile d'après son indice de couleur B−V.
 *
 * Il s'agit d'une approximation visuelle (interpolation linéaire entre quelques points de
 * repère : étoiles bleues autour de B−V = −0,3, blanches vers 0,0, jaunes vers 0,65 comme le
 * Soleil, orangées vers 1,2 et rouges au-delà de 1,6), suffisante pour une carte du ciel. Elle ne
 * prétend pas être une conversion colorimétrique exacte.
 */
object StarColor {
    private val stops = listOf(
        -0.40 to floatArrayOf(0.61f, 0.70f, 1.00f),
        0.00 to floatArrayOf(0.80f, 0.86f, 1.00f),
        0.30 to floatArrayOf(1.00f, 1.00f, 1.00f),
        0.65 to floatArrayOf(1.00f, 0.96f, 0.84f),
        1.20 to floatArrayOf(1.00f, 0.84f, 0.60f),
        1.60 to floatArrayOf(1.00f, 0.72f, 0.46f),
        2.20 to floatArrayOf(1.00f, 0.56f, 0.36f),
    )

    /** Composantes RVB dans [0, 1]. Un indice inconnu (`NaN`) donne du blanc. */
    fun rgb(colorIndex: Double): FloatArray {
        if (colorIndex.isNaN()) return floatArrayOf(1f, 1f, 1f)
        val ci = colorIndex.coerceIn(stops.first().first, stops.last().first)
        for (i in 0 until stops.size - 1) {
            val (c0, rgb0) = stops[i]
            val (c1, rgb1) = stops[i + 1]
            if (ci <= c1) {
                val t = if (c1 == c0) 0.0 else (ci - c0) / (c1 - c0)
                return FloatArray(3) { (rgb0[it] + (rgb1[it] - rgb0[it]) * t).toFloat() }
            }
        }
        return stops.last().second.copyOf()
    }
}
