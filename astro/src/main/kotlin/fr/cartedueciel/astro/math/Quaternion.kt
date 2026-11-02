package fr.cartedueciel.astro.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Quaternion unitaire de rotation (w + xi + yj + zk). */
data class Quaternion(val w: Double, val x: Double, val y: Double, val z: Double) {

    val norm: Double get() = sqrt(w * w + x * x + y * y + z * z)

    fun normalized(): Quaternion {
        val n = norm
        require(n > 0.0) { "Quaternion nul" }
        return Quaternion(w / n, x / n, y / n, z / n)
    }

    fun conjugate() = Quaternion(w, -x, -y, -z)

    operator fun unaryMinus() = Quaternion(-w, -x, -y, -z)

    infix fun dot(o: Quaternion) = w * o.w + x * o.x + y * o.y + z * o.z

    /** Produit de Hamilton : `this * o` applique d'abord `o`, puis `this`. */
    operator fun times(o: Quaternion) = Quaternion(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    /** Angle de rotation (radians, dans [0, π]) séparant deux orientations. */
    fun angleTo(o: Quaternion): Double {
        val d = abs(this dot o).coerceIn(0.0, 1.0)
        return 2.0 * acos(d)
    }

    /** Matrice de rotation équivalente (le quaternion est supposé unitaire). */
    fun toMat3(): Mat3 {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        return Mat3(
            1.0 - 2.0 * (yy + zz), 2.0 * (xy - wz), 2.0 * (xz + wy),
            2.0 * (xy + wz), 1.0 - 2.0 * (xx + zz), 2.0 * (yz - wx),
            2.0 * (xz - wy), 2.0 * (yz + wx), 1.0 - 2.0 * (xx + yy),
        )
    }

    /**
     * Interpolation sphérique vers `target` : `t = 0` renvoie `this`, `t = 1` renvoie `target`.
     * Le chemin le plus court est choisi (q et −q représentent la même rotation).
     */
    fun slerp(target: Quaternion, t: Double): Quaternion {
        var b = target
        var cosTheta = this dot b
        if (cosTheta < 0.0) {
            b = -b
            cosTheta = -cosTheta
        }
        if (cosTheta > 0.9995) {
            // Orientations quasi identiques : interpolation linéaire puis normalisation.
            return Quaternion(
                w + (b.w - w) * t, x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t,
            ).normalized()
        }
        val theta = acos(cosTheta.coerceIn(-1.0, 1.0))
        val sinTheta = sin(theta)
        val wa = sin((1.0 - t) * theta) / sinTheta
        val wb = sin(t * theta) / sinTheta
        return Quaternion(
            wa * w + wb * b.w, wa * x + wb * b.x, wa * y + wb * b.y, wa * z + wb * b.z,
        ).normalized()
    }

    companion object {
        val IDENTITY = Quaternion(1.0, 0.0, 0.0, 0.0)

        /** Rotation d'angle `rad` autour de l'axe unitaire `axis`. */
        fun fromAxisAngle(axis: Vec3, rad: Double): Quaternion {
            val a = axis.normalized()
            val s = sin(rad / 2.0)
            return Quaternion(cos(rad / 2.0), a.x * s, a.y * s, a.z * s)
        }

        /**
         * Quaternion `[x, y, z, w]` tel que fourni par le capteur Android `TYPE_ROTATION_VECTOR`
         * (les trois premières valeurs de l'événement, et la quatrième si présente).
         */
        fun fromAndroidRotationVector(values: FloatArray): Quaternion {
            val qx = values[0].toDouble()
            val qy = values[1].toDouble()
            val qz = values[2].toDouble()
            val qw = if (values.size >= 4 && values[3] != 0f) {
                values[3].toDouble()
            } else {
                sqrt((1.0 - qx * qx - qy * qy - qz * qz).coerceAtLeast(0.0))
            }
            return Quaternion(qw, qx, qy, qz).normalized()
        }

        /** Quaternion d'une matrice de rotation (méthode de Shepperd, stable numériquement). */
        fun fromMat3(m: Mat3): Quaternion {
            val m00 = m[0, 0]; val m01 = m[0, 1]; val m02 = m[0, 2]
            val m10 = m[1, 0]; val m11 = m[1, 1]; val m12 = m[1, 2]
            val m20 = m[2, 0]; val m21 = m[2, 1]; val m22 = m[2, 2]
            val trace = m00 + m11 + m22
            return if (trace > 0.0) {
                val s = sqrt(trace + 1.0) * 2.0
                Quaternion(0.25 * s, (m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s)
            } else if (m00 > m11 && m00 > m22) {
                val s = sqrt(1.0 + m00 - m11 - m22) * 2.0
                Quaternion((m21 - m12) / s, 0.25 * s, (m01 + m10) / s, (m02 + m20) / s)
            } else if (m11 > m22) {
                val s = sqrt(1.0 + m11 - m00 - m22) * 2.0
                Quaternion((m02 - m20) / s, (m01 + m10) / s, 0.25 * s, (m12 + m21) / s)
            } else {
                val s = sqrt(1.0 + m22 - m00 - m11) * 2.0
                Quaternion((m10 - m01) / s, (m02 + m20) / s, (m12 + m21) / s, 0.25 * s)
            }.normalized()
        }
    }
}
