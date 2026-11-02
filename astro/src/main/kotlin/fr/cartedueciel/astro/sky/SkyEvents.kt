package fr.cartedueciel.astro.sky

import fr.cartedueciel.astro.math.Equatorial
import fr.cartedueciel.astro.math.Vec3
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.rotationEqjEqd
import io.github.cosinekitty.astronomy.searchHourAngle
import io.github.cosinekitty.astronomy.searchRiseSet
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Prochains lever, culmination (passage au méridien) et coucher d'un objet, à partir d'un instant.
 * Les instants sont `null` si l'événement ne se produit pas (objet circumpolaire ou jamais levé).
 */
data class SkyEvents(
    val rise: Time?,
    val culmination: Time?,
    val culminationAltitudeDeg: Double?,
    val set: Time?,
    val circumpolar: Boolean,
    val neverRises: Boolean,
)

object SkyEventCalculator {

    /** Hauteur standard du centre d'un astre ponctuel à l'horizon : −34′ de réfraction. */
    const val STANDARD_ALTITUDE_DEG = -0.5667

    /** Vitesse sidérale : degrés d'angle horaire par heure de temps solaire moyen. */
    private const val SIDEREAL_RATE_DEG_PER_HOUR = 360.98564736629 / 24.0

    /**
     * Événements pour le Soleil, la Lune ou une planète (recherche numérique d'Astronomy Engine,
     * réfraction et rayon apparent pris en compte ; fenêtre de recherche de 2 jours).
     */
    fun forBody(body: SolarBody, position: GeoPosition, from: Time): SkyEvents {
        val observer = position.toObserver()
        val rise = searchRiseSet(body.aeBody, observer, Direction.Rise, from, 2.0)
        val set = searchRiseSet(body.aeBody, observer, Direction.Set, from, 2.0)
        val culm = runCatching { searchHourAngle(body.aeBody, observer, 0.0, from) }.getOrNull()
        val circumpolar = rise == null && set == null && (culm?.hor?.altitude ?: -1.0) > 0.0
        val neverRises = rise == null && set == null && !circumpolar
        return SkyEvents(rise, culm?.time, culm?.hor?.altitude, set, circumpolar, neverRises)
    }

    /**
     * Événements pour un point fixe de la sphère céleste (étoile, centre d'une constellation),
     * par la formule classique de l'angle horaire au lever :
     * cos H0 = (sin h0 − sin φ sin δ) / (cos φ cos δ), avec h0 = −34′.
     *
     * Les coordonnées sont précessées à la date ; la précision est de l'ordre de la minute
     * (variation de la déclinaison et de la réfraction au cours de la journée négligées).
     */
    fun forFixedPoint(eqjDir: Vec3, position: GeoPosition, from: Time): SkyEvents {
        val rot = SkyFrame.fromAstronomyEngine(rotationEqjEqd(from))
        val (raRad, decRad) = Equatorial.toRaDec(rot * eqjDir)
        val phi = Math.toRadians(position.latitudeDeg)
        val h0 = Math.toRadians(STANDARD_ALTITUDE_DEG)
        val frame = SkyFrame(from, position)
        val lstDeg = frame.localSiderealTimeHours * 15.0
        val raDeg = Math.toDegrees(raRad)
        val hourAngleNowDeg = normalize180(lstDeg - raDeg)

        val culminationAlt = 90.0 - abs(position.latitudeDeg - Math.toDegrees(decRad))
        val culminationTime = from.addDays(hoursUntil(hourAngleNowDeg, 0.0) / 24.0)

        val cosH0 = (sin(h0) - sin(phi) * sin(decRad)) / (cos(phi) * cos(decRad))
        return when {
            cosH0 < -1.0 -> SkyEvents(null, culminationTime, culminationAlt, null, circumpolar = true, neverRises = false)
            cosH0 > 1.0 -> SkyEvents(null, null, culminationAlt, null, circumpolar = false, neverRises = true)
            else -> {
                val h0Deg = Math.toDegrees(acos(cosH0))
                val rise = from.addDays(hoursUntil(hourAngleNowDeg, -h0Deg) / 24.0)
                val set = from.addDays(hoursUntil(hourAngleNowDeg, h0Deg) / 24.0)
                SkyEvents(rise, culminationTime, culminationAlt, set, circumpolar = false, neverRises = false)
            }
        }
    }

    /** Heures (temps solaire) avant que l'angle horaire passe de `nowDeg` à `targetDeg`. */
    private fun hoursUntil(nowDeg: Double, targetDeg: Double): Double {
        var delta = targetDeg - nowDeg
        while (delta < 0.0) delta += 360.0
        while (delta >= 360.0) delta -= 360.0
        return delta / SIDEREAL_RATE_DEG_PER_HOUR
    }

    private fun normalize180(deg: Double): Double {
        var d = deg % 360.0
        if (d >= 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }
}
