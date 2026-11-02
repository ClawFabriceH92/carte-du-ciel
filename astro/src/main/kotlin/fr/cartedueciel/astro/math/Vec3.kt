package fr.cartedueciel.astro.math

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Vecteur 3D en double précision (immuable). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    operator fun div(k: Double) = Vec3(x / k, y / k, z / k)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    val length: Double get() = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val l = length
        require(l > 0.0) { "Vecteur nul : pas de direction" }
        return this / l
    }

    /** Angle (radians) entre deux vecteurs non nuls. */
    fun angleTo(o: Vec3): Double {
        val c = (this dot o) / (length * o.length)
        return acos(c.coerceIn(-1.0, 1.0))
    }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Y = Vec3(0.0, 1.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)
    }
}

/**
 * Coordonnées horizontales : azimut compté depuis le nord vers l'est, hauteur au-dessus de
 * l'horizon, en degrés.
 */
data class Horizontal(val azimuthDeg: Double, val altitudeDeg: Double)

/**
 * Repère local « ENU » utilisé dans toute l'application pour le ciel :
 * x = est, y = nord géographique, z = zénith. Repère direct (x × y = z).
 */
object Enu {
    /** Direction unitaire correspondant à un azimut et une hauteur (degrés). */
    fun fromHorizontal(azimuthDeg: Double, altitudeDeg: Double): Vec3 {
        val az = Math.toRadians(azimuthDeg)
        val alt = Math.toRadians(altitudeDeg)
        val c = cos(alt)
        return Vec3(c * sin(az), c * cos(az), sin(alt))
    }

    fun fromHorizontal(h: Horizontal): Vec3 = fromHorizontal(h.azimuthDeg, h.altitudeDeg)

    /** Azimut [0, 360[ et hauteur [−90, 90] d'une direction (non nécessairement unitaire). */
    fun toHorizontal(v: Vec3): Horizontal {
        val n = v.normalized()
        val alt = Math.toDegrees(kotlin.math.asin(n.z.coerceIn(-1.0, 1.0)))
        var az = Math.toDegrees(atan2(n.x, n.y))
        if (az < 0.0) az += 360.0
        if (az >= 360.0) az -= 360.0
        return Horizontal(az, alt)
    }
}

/**
 * Repère équatorial J2000 : x vers le point vernal, z vers le pôle nord céleste,
 * y complétant le trièdre direct (ascension droite 6 h, déclinaison 0).
 */
object Equatorial {
    /** Direction unitaire d'un point de coordonnées (ascension droite, déclinaison) en radians. */
    fun fromRaDec(raRad: Double, decRad: Double): Vec3 {
        val c = cos(decRad)
        return Vec3(c * cos(raRad), c * sin(raRad), sin(decRad))
    }

    /** (ascension droite [0, 2π[, déclinaison) en radians d'une direction. */
    fun toRaDec(v: Vec3): Pair<Double, Double> {
        val n = v.normalized()
        var ra = atan2(n.y, n.x)
        if (ra < 0.0) ra += 2.0 * Math.PI
        val dec = kotlin.math.asin(n.z.coerceIn(-1.0, 1.0))
        return ra to dec
    }
}
