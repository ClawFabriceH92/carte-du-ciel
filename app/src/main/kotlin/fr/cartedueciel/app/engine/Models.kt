package fr.cartedueciel.app.engine

import fr.cartedueciel.astro.sky.MoonPhaseName
import fr.cartedueciel.astro.sky.SkyEvents
import fr.cartedueciel.astro.sky.SolarBody
import fr.cartedueciel.sensors.CompassQuality
import fr.cartedueciel.sensors.OrientationSource

/** Objet du ciel désigné par le réticule, la recherche ou une fiche. */
sealed class SkyTarget {
    data class Star(val index: Int) : SkyTarget()
    data class Body(val body: SolarBody) : SkyTarget()
    data class Constellation(val index: Int) : SkyTarget()
}

enum class ObjectType { STAR, PLANET, SUN, MOON, CONSTELLATION }

/** Guidage vers un objet recherché. */
data class GuidanceState(
    val target: SkyTarget,
    val name: String,
    /** Angle de la flèche à l'écran, en radians, 0 = vers le haut, sens horaire. */
    val arrowAngleRad: Float,
    val onScreen: Boolean,
    val distanceDeg: Double,
    val centered: Boolean,
)

/** État de l'affichage tête haute, rafraîchi à ~10 Hz pour Compose. */
data class HudState(
    val target: SkyTarget? = null,
    val targetName: String? = null,
    val targetSubtitle: String? = null,
    val lookAzimuthDeg: Double = 0.0,
    val lookAltitudeDeg: Double = 0.0,
    val compassQuality: CompassQuality = CompassQuality.UNKNOWN,
    val headingAccuracyDeg: Double? = null,
    val orientationSource: OrientationSource = OrientationSource.NONE,
    val guidance: GuidanceState? = null,
    val declinationDeg: Double = 0.0,
    val wmmValid: Boolean = true,
)

/** Contenu de la fiche objet. */
data class ObjectDetails(
    val target: SkyTarget,
    val title: String,
    val subtitle: String?,
    val type: ObjectType,
    val constellationName: String?,
    val magnitude: Double?,
    val distancePc: Double?,
    val distanceAu: Double?,
    val altitudeDeg: Double,
    val azimuthDeg: Double,
    val events: SkyEvents?,
    val moonPhase: MoonPhaseName?,
    val phaseFraction: Double?,
    val starCount: Int?,
)

/** Libellés dépendant de la langue fournis par l'interface. */
data class SceneLabels(
    val cardinalNorth: String,
    val cardinalEast: String,
    val cardinalSouth: String,
    val cardinalWest: String,
    val french: Boolean,
)
