package fr.cartedueciel.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class GridMode { NONE, AZIMUTHAL, EQUATORIAL }
enum class DistanceUnit { LIGHT_YEARS, PARSECS }
enum class LanguageChoice { SYSTEM, FR, EN }
enum class LocationMode { AUTO, MANUAL }

/** Réglages persistants de l'application (DataStore Preferences). */
data class SkySettings(
    val magnitudeLimit: Float = 6.5f,
    val showStars: Boolean = true,
    val showLines: Boolean = true,
    val showNames: Boolean = true,
    val showPlanets: Boolean = true,
    val showHorizon: Boolean = true,
    val showGround: Boolean = true,
    val grid: GridMode = GridMode.NONE,
    val nightMode: Boolean = false,
    val cameraBackground: Boolean = false,
    val manualMode: Boolean = false,
    /** Lissage du mouvement, 0 (brut) à 1 (très lissé). */
    val smoothing: Float = 0.5f,
    /** Décalage manuel de la boussole (degrés, positif vers l'est). */
    val compassOffsetDeg: Float = 0f,
    /** Densité des libellés, 0 à 1. */
    val labelDensity: Float = 0.6f,
    val locationMode: LocationMode = LocationMode.AUTO,
    val manualLatitude: Double = 48.8566,
    val manualLongitude: Double = 2.3522,
    val manualPlaceName: String = "Paris",
    val distanceUnit: DistanceUnit = DistanceUnit.LIGHT_YEARS,
    val language: LanguageChoice = LanguageChoice.SYSTEM,
    /** Facteur de correction du champ de vision caméra estimé. */
    val cameraFovScale: Float = 1f,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val MIN_MAGNITUDE = 1.0f
        const val MAX_MAGNITUDE = 6.5f
        const val PRESET_CITY = 3.5f
        const val PRESET_COUNTRY = 5.5f
        const val PRESET_DARK = 6.5f
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "carte_du_ciel_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val magnitude = floatPreferencesKey("magnitude_limit")
        val showStars = booleanPreferencesKey("show_stars")
        val showLines = booleanPreferencesKey("show_lines")
        val showNames = booleanPreferencesKey("show_names")
        val showPlanets = booleanPreferencesKey("show_planets")
        val showHorizon = booleanPreferencesKey("show_horizon")
        val showGround = booleanPreferencesKey("show_ground")
        val grid = stringPreferencesKey("grid")
        val nightMode = booleanPreferencesKey("night_mode")
        val camera = booleanPreferencesKey("camera_background")
        val manualMode = booleanPreferencesKey("manual_mode")
        val smoothing = floatPreferencesKey("smoothing")
        val compassOffset = floatPreferencesKey("compass_offset_deg")
        val labelDensity = floatPreferencesKey("label_density")
        val locationMode = stringPreferencesKey("location_mode")
        val manualLat = doublePreferencesKey("manual_latitude")
        val manualLon = doublePreferencesKey("manual_longitude")
        val manualName = stringPreferencesKey("manual_place_name")
        val distanceUnit = stringPreferencesKey("distance_unit")
        val language = stringPreferencesKey("language")
        val cameraFovScale = floatPreferencesKey("camera_fov_scale")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<SkySettings> = context.settingsDataStore.data.map { p ->
        val d = SkySettings()
        SkySettings(
            magnitudeLimit = p[Keys.magnitude] ?: d.magnitudeLimit,
            showStars = p[Keys.showStars] ?: d.showStars,
            showLines = p[Keys.showLines] ?: d.showLines,
            showNames = p[Keys.showNames] ?: d.showNames,
            showPlanets = p[Keys.showPlanets] ?: d.showPlanets,
            showHorizon = p[Keys.showHorizon] ?: d.showHorizon,
            showGround = p[Keys.showGround] ?: d.showGround,
            grid = p[Keys.grid]?.let { runCatching { GridMode.valueOf(it) }.getOrNull() } ?: d.grid,
            nightMode = p[Keys.nightMode] ?: d.nightMode,
            cameraBackground = p[Keys.camera] ?: d.cameraBackground,
            manualMode = p[Keys.manualMode] ?: d.manualMode,
            smoothing = p[Keys.smoothing] ?: d.smoothing,
            compassOffsetDeg = p[Keys.compassOffset] ?: d.compassOffsetDeg,
            labelDensity = p[Keys.labelDensity] ?: d.labelDensity,
            locationMode = p[Keys.locationMode]?.let { runCatching { LocationMode.valueOf(it) }.getOrNull() } ?: d.locationMode,
            manualLatitude = p[Keys.manualLat] ?: d.manualLatitude,
            manualLongitude = p[Keys.manualLon] ?: d.manualLongitude,
            manualPlaceName = p[Keys.manualName] ?: d.manualPlaceName,
            distanceUnit = p[Keys.distanceUnit]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: d.distanceUnit,
            language = p[Keys.language]?.let { runCatching { LanguageChoice.valueOf(it) }.getOrNull() } ?: d.language,
            cameraFovScale = p[Keys.cameraFovScale] ?: d.cameraFovScale,
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
        )
    }

    suspend fun update(transform: (SkySettings) -> SkySettings) {
        context.settingsDataStore.edit { p ->
            val current = settingsFrom(p)
            val s = transform(current)
            p[Keys.magnitude] = s.magnitudeLimit
            p[Keys.showStars] = s.showStars
            p[Keys.showLines] = s.showLines
            p[Keys.showNames] = s.showNames
            p[Keys.showPlanets] = s.showPlanets
            p[Keys.showHorizon] = s.showHorizon
            p[Keys.showGround] = s.showGround
            p[Keys.grid] = s.grid.name
            p[Keys.nightMode] = s.nightMode
            p[Keys.camera] = s.cameraBackground
            p[Keys.manualMode] = s.manualMode
            p[Keys.smoothing] = s.smoothing
            p[Keys.compassOffset] = s.compassOffsetDeg
            p[Keys.labelDensity] = s.labelDensity
            p[Keys.locationMode] = s.locationMode.name
            p[Keys.manualLat] = s.manualLatitude
            p[Keys.manualLon] = s.manualLongitude
            p[Keys.manualName] = s.manualPlaceName
            p[Keys.distanceUnit] = s.distanceUnit.name
            p[Keys.language] = s.language.name
            p[Keys.cameraFovScale] = s.cameraFovScale
            p[Keys.onboardingDone] = s.onboardingDone
        }
    }

    private fun settingsFrom(p: Preferences): SkySettings {
        val d = SkySettings()
        return SkySettings(
            magnitudeLimit = p[Keys.magnitude] ?: d.magnitudeLimit,
            showStars = p[Keys.showStars] ?: d.showStars,
            showLines = p[Keys.showLines] ?: d.showLines,
            showNames = p[Keys.showNames] ?: d.showNames,
            showPlanets = p[Keys.showPlanets] ?: d.showPlanets,
            showHorizon = p[Keys.showHorizon] ?: d.showHorizon,
            showGround = p[Keys.showGround] ?: d.showGround,
            grid = p[Keys.grid]?.let { runCatching { GridMode.valueOf(it) }.getOrNull() } ?: d.grid,
            nightMode = p[Keys.nightMode] ?: d.nightMode,
            cameraBackground = p[Keys.camera] ?: d.cameraBackground,
            manualMode = p[Keys.manualMode] ?: d.manualMode,
            smoothing = p[Keys.smoothing] ?: d.smoothing,
            compassOffsetDeg = p[Keys.compassOffset] ?: d.compassOffsetDeg,
            labelDensity = p[Keys.labelDensity] ?: d.labelDensity,
            locationMode = p[Keys.locationMode]?.let { runCatching { LocationMode.valueOf(it) }.getOrNull() } ?: d.locationMode,
            manualLatitude = p[Keys.manualLat] ?: d.manualLatitude,
            manualLongitude = p[Keys.manualLon] ?: d.manualLongitude,
            manualPlaceName = p[Keys.manualName] ?: d.manualPlaceName,
            distanceUnit = p[Keys.distanceUnit]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: d.distanceUnit,
            language = p[Keys.language]?.let { runCatching { LanguageChoice.valueOf(it) }.getOrNull() } ?: d.language,
            cameraFovScale = p[Keys.cameraFovScale] ?: d.cameraFovScale,
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
        )
    }
}
