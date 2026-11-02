package fr.cartedueciel.astro.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Matrice 3×3 en double précision, stockée ligne par ligne (`m[row * 3 + col]`).
 * Convention : `this * v` applique la transformation au vecteur colonne `v`.
 */
class Mat3(val m: DoubleArray) {
    init {
        require(m.size == 9) { "Une matrice 3x3 a 9 coefficients" }
    }

    constructor(
        a00: Double, a01: Double, a02: Double,
        a10: Double, a11: Double, a12: Double,
        a20: Double, a21: Double, a22: Double,
    ) : this(doubleArrayOf(a00, a01, a02, a10, a11, a12, a20, a21, a22))

    operator fun get(row: Int, col: Int): Double = m[row * 3 + col]

    operator fun times(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    operator fun times(o: Mat3): Mat3 {
        val r = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var s = 0.0
            for (k in 0 until 3) s += this[i, k] * o[k, j]
            r[i * 3 + j] = s
        }
        return Mat3(r)
    }

    fun transposed() = Mat3(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])

    fun determinant(): Double =
        m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])

    /** Vrai si la matrice est orthonormale directe (rotation) à `eps` près. */
    fun isRotation(eps: Double = 1e-9): Boolean {
        val p = this * transposed()
        for (i in 0 until 3) for (j in 0 until 3) {
            val expected = if (i == j) 1.0 else 0.0
            if (abs(p[i, j] - expected) > eps) return false
        }
        return abs(determinant() - 1.0) < eps
    }

    /** Colonne `col` sous forme de vecteur. */
    fun column(col: Int) = Vec3(m[col], m[3 + col], m[6 + col])

    /** Ligne `row` sous forme de vecteur. */
    fun row(row: Int) = Vec3(m[row * 3], m[row * 3 + 1], m[row * 3 + 2])

    /**
     * Copie en tableau de flottants « column-major » 4×4 (format attendu par OpenGL ES),
     * la rotation occupant le bloc 3×3 supérieur gauche.
     */
    fun toGl4x4(): FloatArray {
        val out = FloatArray(16)
        for (row in 0 until 3) for (col in 0 until 3) out[col * 4 + row] = this[row, col].toFloat()
        out[15] = 1f
        return out
    }

    fun toFloatArray(): FloatArray = FloatArray(9) { m[it].toFloat() }

    override fun toString(): String = (0 until 3).joinToString("\n") { r -> "[%.6f %.6f %.6f]".format(this[r, 0], this[r, 1], this[r, 2]) }

    companion object {
        val IDENTITY = Mat3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

        /** Rotation d'angle `rad` autour de l'axe x (sens direct). */
        fun rotationX(rad: Double): Mat3 {
            val c = cos(rad); val s = sin(rad)
            return Mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c)
        }

        /** Rotation d'angle `rad` autour de l'axe y (sens direct). */
        fun rotationY(rad: Double): Mat3 {
            val c = cos(rad); val s = sin(rad)
            return Mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c)
        }

        /** Rotation d'angle `rad` autour de l'axe z (sens direct, de x vers y). */
        fun rotationZ(rad: Double): Mat3 {
            val c = cos(rad); val s = sin(rad)
            return Mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0)
        }

        /** Matrice dont les colonnes sont les vecteurs donnés. */
        fun fromColumns(c0: Vec3, c1: Vec3, c2: Vec3) = Mat3(
            c0.x, c1.x, c2.x,
            c0.y, c1.y, c2.y,
            c0.z, c1.z, c2.z,
        )

        /** Matrice dont les lignes sont les vecteurs donnés. */
        fun fromRows(r0: Vec3, r1: Vec3, r2: Vec3) = Mat3(
            r0.x, r0.y, r0.z,
            r1.x, r1.y, r1.z,
            r2.x, r2.y, r2.z,
        )

        /**
         * Construit une matrice à partir du tableau de 9 ou 16 flottants « row-major » fourni par
         * `SensorManager.getRotationMatrixFromVector` (les 9 premiers éléments d'un tableau de 16
         * sont les lignes 0..2 par blocs de 4).
         */
        fun fromAndroidRotationMatrix(r: FloatArray): Mat3 = when (r.size) {
            9 -> Mat3(DoubleArray(9) { r[it].toDouble() })
            16 -> Mat3(
                r[0].toDouble(), r[1].toDouble(), r[2].toDouble(),
                r[4].toDouble(), r[5].toDouble(), r[6].toDouble(),
                r[8].toDouble(), r[9].toDouble(), r[10].toDouble(),
            )
            else -> throw IllegalArgumentException("Taille de matrice inattendue : ${r.size}")
        }
    }
}
