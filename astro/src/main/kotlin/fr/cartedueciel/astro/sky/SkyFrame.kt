package fr.cartedueciel.astro.sky

import fr.cartedueciel.astro.math.Enu
import fr.cartedueciel.astro.math.Horizontal
import fr.cartedueciel.astro.math.Mat3
import fr.cartedueciel.astro.math.Vec3
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.RotationMatrix
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.refractionAngle
import io.github.cosinekitty.astronomy.inverseRefractionAngle
import io.github.cosinekitty.astronomy.rotationEqjHor
import io.github.cosinekitty.astronomy.siderealTime

/**
 * Repère du ciel local à un instant et pour un observateur donnés.
 *
 * Fournit la matrice « équatorial J2000 → horizontal local (ENU) » de l'instant, précession et
 * nutation incluses (calculée par Astronomy Engine), ainsi que la réfraction atmosphérique.
 * Cette matrice change lentement : un recalcul par seconde suffit, comme indiqué dans le cahier
 * des charges (section 5).
 */
class SkyFrame(val time: Time, val position: GeoPosition) {

    /** Matrice J2000 → ENU (sans réfraction). */
    val eqjToEnu: Mat3 = HOR_TO_ENU * fromAstronomyEngine(rotationEqjHor(time, position.toObserver()))

    /** Matrice ENU → J2000. */
    val enuToEqj: Mat3 = eqjToEnu.transposed()

    /** Temps sidéral local apparent (heures, [0, 24[). */
    val localSiderealTimeHours: Double by lazy {
        var lst = siderealTime(time) + position.longitudeDeg / 15.0
        lst %= 24.0
        if (lst < 0.0) lst += 24.0
        lst
    }

    /** Direction ENU du Soleil (géocentrique, sans réfraction), pour le crépuscule et l'éclairage. */
    val sunEnu: Vec3 by lazy {
        val eq = equator(Body.Sun, time, position.toObserver(), EquatorEpoch.J2000, Aberration.Corrected)
        toEnu(Vec3(eq.vec.x, eq.vec.y, eq.vec.z).normalized(), applyRefraction = false)
    }

    /** Hauteur vraie du Soleil (degrés). */
    val sunAltitudeDeg: Double by lazy { Enu.toHorizontal(sunEnu).altitudeDeg }

    val twilight: Twilight get() = Twilight.fromSunAltitude(sunAltitudeDeg)

    /**
     * Direction ENU (unitaire) d'une direction J2000, avec ou sans réfraction atmosphérique.
     */
    fun toEnu(eqjDir: Vec3, applyRefraction: Boolean = true): Vec3 {
        val v = eqjToEnu * eqjDir
        return if (applyRefraction) withRefraction(v) else v
    }

    /** Coordonnées horizontales apparentes (réfraction comprise) d'une direction J2000. */
    fun horizontalOf(eqjDir: Vec3, applyRefraction: Boolean = true): Horizontal =
        Enu.toHorizontal(toEnu(eqjDir, applyRefraction))

    /** Direction J2000 d'une direction ENU apparente (annule la réfraction si demandé). */
    fun toEqj(enuDir: Vec3, removeRefraction: Boolean = true): Vec3 {
        val v = if (removeRefraction) withoutRefraction(enuDir) else enuDir
        return enuToEqj * v
    }

    companion object {
        /**
         * Repère horizontal d'Astronomy Engine (« HOR » : x = nord, y = ouest, z = zénith)
         * vers le repère ENU de l'application (x = est, y = nord, z = zénith).
         */
        val HOR_TO_ENU = Mat3(
            0.0, -1.0, 0.0,
            1.0, 0.0, 0.0,
            0.0, 0.0, 1.0,
        )

        /**
         * Convertit une matrice Astronomy Engine. Sa méthode `rotate` calcule
         * `out_i = Σ_j rot[j][i] · v_j` : l'élément (ligne i, colonne j) vaut donc `rot[j][i]`.
         */
        fun fromAstronomyEngine(r: RotationMatrix): Mat3 {
            val m = DoubleArray(9)
            for (i in 0 until 3) for (j in 0 until 3) m[i * 3 + j] = r.rot[j][i]
            return Mat3(m)
        }

        /** Hauteur apparente (degrés) d'une hauteur vraie, réfraction « normale » d'Astronomy Engine. */
        fun apparentAltitudeDeg(trueAltitudeDeg: Double): Double =
            trueAltitudeDeg + refractionAngle(Refraction.Normal, trueAltitudeDeg)

        /** Hauteur vraie (degrés) d'une hauteur apparente. */
        fun trueAltitudeDeg(apparentAltitudeDeg: Double): Double =
            apparentAltitudeDeg + inverseRefractionAngle(Refraction.Normal, apparentAltitudeDeg)

        /** Applique la réfraction à une direction ENU vraie. */
        fun withRefraction(enuTrue: Vec3): Vec3 {
            val h = Enu.toHorizontal(enuTrue)
            val apparent = apparentAltitudeDeg(h.altitudeDeg)
            return if (apparent == h.altitudeDeg) enuTrue.normalized() else Enu.fromHorizontal(h.azimuthDeg, apparent)
        }

        /** Retire la réfraction d'une direction ENU apparente. */
        fun withoutRefraction(enuApparent: Vec3): Vec3 {
            val h = Enu.toHorizontal(enuApparent)
            val trueAlt = trueAltitudeDeg(h.altitudeDeg)
            return if (trueAlt == h.altitudeDeg) enuApparent.normalized() else Enu.fromHorizontal(h.azimuthDeg, trueAlt)
        }
    }
}

/** Phase du crépuscule d'après la hauteur du Soleil (définitions usuelles : −6°, −12°, −18°). */
enum class Twilight {
    DAY, CIVIL, NAUTICAL, ASTRONOMICAL, NIGHT;

    companion object {
        fun fromSunAltitude(altDeg: Double): Twilight = when {
            altDeg > 0.0 -> DAY
            altDeg > -6.0 -> CIVIL
            altDeg > -12.0 -> NAUTICAL
            altDeg > -18.0 -> ASTRONOMICAL
            else -> NIGHT
        }
    }
}
