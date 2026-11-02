package fr.cartedueciel.astro.sky

import fr.cartedueciel.astro.math.Vec3
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase

/** Corps du Système solaire affichés par l'application. */
enum class SolarBody(val aeBody: Body, val nameFr: String, val nameEn: String) {
    SUN(Body.Sun, "Soleil", "Sun"),
    MOON(Body.Moon, "Lune", "Moon"),
    MERCURY(Body.Mercury, "Mercure", "Mercury"),
    VENUS(Body.Venus, "Vénus", "Venus"),
    MARS(Body.Mars, "Mars", "Mars"),
    JUPITER(Body.Jupiter, "Jupiter", "Jupiter"),
    SATURN(Body.Saturn, "Saturne", "Saturn"),
    URANUS(Body.Uranus, "Uranus", "Uranus"),
    NEPTUNE(Body.Neptune, "Neptune", "Neptune");

    val isPlanet: Boolean get() = this != SUN && this != MOON
}

/** Phases lunaires nommées. */
enum class MoonPhaseName(val fr: String, val en: String) {
    NEW("nouvelle lune", "new moon"),
    WAXING_CRESCENT("premier croissant", "waxing crescent"),
    FIRST_QUARTER("premier quartier", "first quarter"),
    WAXING_GIBBOUS("gibbeuse croissante", "waxing gibbous"),
    FULL("pleine lune", "full moon"),
    WANING_GIBBOUS("gibbeuse décroissante", "waning gibbous"),
    LAST_QUARTER("dernier quartier", "last quarter"),
    WANING_CRESCENT("dernier croissant", "waning crescent");

    companion object {
        /** D'après l'angle de phase écliptique Lune − Soleil (0° nouvelle, 180° pleine). */
        fun fromPhaseAngle(phaseDeg: Double): MoonPhaseName {
            val p = ((phaseDeg % 360.0) + 360.0) % 360.0
            return when {
                p < 22.5 -> NEW
                p < 67.5 -> WAXING_CRESCENT
                p < 112.5 -> FIRST_QUARTER
                p < 157.5 -> WAXING_GIBBOUS
                p < 202.5 -> FULL
                p < 247.5 -> WANING_GIBBOUS
                p < 292.5 -> LAST_QUARTER
                p < 337.5 -> WANING_CRESCENT
                else -> NEW
            }
        }
    }
}

/**
 * État apparent d'un corps du Système solaire pour un observateur.
 *
 * @property eqjDir direction unitaire topocentrique en coordonnées équatoriales J2000
 * @property distanceAu distance à l'observateur (unités astronomiques)
 * @property magnitude magnitude visuelle apparente
 * @property phaseFraction fraction éclairée du disque (0 à 1)
 * @property phaseAngleDeg angle de phase écliptique (Lune seulement : 0° nouvelle, 180° pleine), sinon NaN
 */
data class BodyState(
    val body: SolarBody,
    val time: Time,
    val eqjDir: Vec3,
    val distanceAu: Double,
    val magnitude: Double,
    val phaseFraction: Double,
    val phaseAngleDeg: Double,
    val raHours: Double,
    val decDeg: Double,
) {
    val moonPhaseName: MoonPhaseName? get() = if (body == SolarBody.MOON) MoonPhaseName.fromPhaseAngle(phaseAngleDeg) else null
}

/**
 * Éphémérides du Soleil, de la Lune et des planètes (Astronomy Engine : VSOP87 / NOVAS, avec
 * correction de la parallaxe topocentrique et de l'aberration). À recalculer environ une fois
 * par minute (cahier des charges, section 5).
 */
object SolarSystem {

    fun state(body: SolarBody, time: Time, position: GeoPosition): BodyState {
        val observer = position.toObserver()
        val eq = equator(body.aeBody, time, observer, EquatorEpoch.J2000, Aberration.Corrected)
        val dir = Vec3(eq.vec.x, eq.vec.y, eq.vec.z).normalized()
        val illum = illumination(body.aeBody, time)
        val phaseAngle = if (body == SolarBody.MOON) moonPhase(time) else Double.NaN
        return BodyState(
            body = body,
            time = time,
            eqjDir = dir,
            distanceAu = eq.dist,
            magnitude = illum.mag,
            phaseFraction = illum.phaseFraction,
            phaseAngleDeg = phaseAngle,
            raHours = eq.ra,
            decDeg = eq.dec,
        )
    }

    fun all(time: Time, position: GeoPosition): List<BodyState> = SolarBody.entries.map { state(it, time, position) }
}
