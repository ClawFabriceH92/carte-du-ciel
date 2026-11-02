package fr.cartedueciel.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.cartedueciel.app.data.LanguageChoice
import fr.cartedueciel.app.data.LocationMode
import fr.cartedueciel.app.data.LocationRepository
import fr.cartedueciel.app.data.LocationState
import fr.cartedueciel.app.data.SettingsRepository
import fr.cartedueciel.app.data.SkySettings
import fr.cartedueciel.app.engine.ObjectDetails
import fr.cartedueciel.app.engine.SceneLabels
import fr.cartedueciel.app.engine.HudState
import fr.cartedueciel.app.engine.SkyEngine
import fr.cartedueciel.app.engine.SkyTarget
import fr.cartedueciel.catalog.SearchIndex
import fr.cartedueciel.catalog.SearchResult
import fr.cartedueciel.catalog.SearchTarget
import fr.cartedueciel.catalog.SkyCatalog
import fr.cartedueciel.catalog.SkyCatalogFormat
import fr.cartedueciel.render.SkyGLSurfaceView
import fr.cartedueciel.sensors.CoarseLocationProvider
import fr.cartedueciel.sensors.OrientationProvider
import fr.cartedueciel.sensors.OrientationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class Screen { LOADING, ONBOARDING, SKY, SETTINGS, ABOUT }

/** Panneaux superposés à la vue ciel. */
sealed class Sheet {
    data object Search : Sheet()
    data object Layers : Sheet()
    data class Object(val details: ObjectDetails) : Sheet()
    data object Calibrate : Sheet()
    data object Location : Sheet()
}

class SkyViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val locationRepo = LocationRepository(CoarseLocationProvider(app))
    private val orientationProvider = OrientationProvider(app)

    val orientationSource: OrientationSource get() = orientationProvider.source
    val orientationAvailable: Boolean get() = orientationProvider.isAvailable

    private val _settings = MutableStateFlow(SkySettings())
    val settings: StateFlow<SkySettings> = _settings
    val location: StateFlow<LocationState> = locationRepo.state

    private val _screen = MutableStateFlow(Screen.LOADING)
    val screen: StateFlow<Screen> = _screen
    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet
    private val _hud = MutableStateFlow(HudState())
    val hud: StateFlow<HudState> = _hud
    private val _manualTimeWarning = MutableStateFlow(false)
    val manualTimeWarning: StateFlow<Boolean> = _manualTimeWarning
    private val _cameraPermission = MutableStateFlow(false)
    val cameraPermission: StateFlow<Boolean> = _cameraPermission
    private val _message = MutableStateFlow<String?>(null)
    /** Message transitoire à afficher (snackbar). */
    val message: StateFlow<String?> = _message

    private var catalog: SkyCatalog? = null
    private var searchIndex: SearchIndex? = null
    private var engine: SkyEngine? = null
    private var glView: SkyGLSurfaceView? = null
    private var tickerJob: Job? = null
    private var sensorJob: Job? = null
    private var settingsLoaded = false
    private var running = false

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                getApplication<Application>().assets.open("catalog/sky_catalog.bin").use { SkyCatalogFormat.read(it) }
            }
            catalog = loaded
            val e = SkyEngine(loaded, onScene = { s -> glView?.setScene(s) }, onView = { v -> glView?.setView(v) })
            e.onTargetCentered = { vibrate() }
            engine = e
            searchIndex = SearchIndex(loaded, currentLanguageTag())
            viewModelScope.launch { e.hud.collect { _hud.value = it } }
            e.setLabels(sceneLabels())
            e.setPosition(location.value.position)
            settingsRepo.settings.collectLatest { s ->
                val first = !settingsLoaded
                settingsLoaded = true
                val previous = _settings.value
                _settings.value = s
                e.setSettings(s)
                if (first) {
                    _screen.value = if (s.onboardingDone) Screen.SKY else Screen.ONBOARDING
                    applyLanguage(s.language, persist = false)
                }
                if (first || previous.locationMode != s.locationMode || previous.manualLatitude != s.manualLatitude ||
                    previous.manualLongitude != s.manualLongitude
                ) {
                    refreshLocation()
                }
                if (previous.smoothing != s.smoothing || previous.manualMode != s.manualMode) restartSensors()
                if (!s.manualMode && !orientationProvider.isAvailable) {
                    // Aucun capteur : le mode manuel est imposé.
                    settingsRepo.update { it.copy(manualMode = true) }
                }
            }
        }
        viewModelScope.launch { location.collect { engine?.setPosition(it.position) } }
    }

    // ---- Cycle de vie --------------------------------------------------------------------------

    /** À appeler quand l'écran est visible : démarre l'horloge et les capteurs. */
    fun start() {
        running = true
        _manualTimeWarning.value = isTimeSetManually(getApplication())
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                engine?.tick(System.currentTimeMillis())
                delay(1000L)
            }
        }
        restartSensors()
    }

    /** À appeler quand l'écran n'est plus visible : arrête capteurs et horloge (économie de batterie). */
    fun stop() {
        running = false
        tickerJob?.cancel(); tickerJob = null
        sensorJob?.cancel(); sensorJob = null
    }

    private fun restartSensors() {
        sensorJob?.cancel()
        if (!running || _settings.value.manualMode || !orientationProvider.isAvailable) return
        sensorJob = viewModelScope.launch(Dispatchers.Default) {
            orientationProvider.orientations(_settings.value.smoothing).collect { sample -> engine?.onOrientation(sample) }
        }
    }

    fun attachRenderer(view: SkyGLSurfaceView) {
        glView = view
        engine?.let { e -> e.tick(System.currentTimeMillis()) }
    }

    fun detachRenderer(view: SkyGLSurfaceView) {
        if (glView === view) glView = null
    }

    fun onViewportChanged(widthPx: Int, heightPx: Int) = engine?.setViewport(widthPx, heightPx) ?: Unit
    fun onDisplayRotation(rotation: Int) = engine?.setDisplayRotation(rotation) ?: Unit
    fun onCameraFov(longSideDeg: Double?) = engine?.setCameraFov(longSideDeg) ?: Unit
    fun onManualGesture(panX: Float, panY: Float, zoom: Float, w: Int, h: Int) = engine?.onManualGesture(panX, panY, zoom, w, h) ?: Unit
    fun onCameraPermission(granted: Boolean) { _cameraPermission.value = granted }

    // ---- Navigation ----------------------------------------------------------------------------

    fun navigate(screen: Screen) { _screen.value = screen }
    fun showSheet(sheet: Sheet?) { _sheet.value = sheet }
    fun dismissSheet() { _sheet.value = null }
    fun showMessage(text: String?) { _message.value = text }

    fun openTarget(target: SkyTarget) {
        val e = engine ?: return
        _sheet.value = Sheet.Object(e.details(target))
    }

    fun openCurrentTarget() { hud.value.target?.let { openTarget(it) } }

    // ---- Réglages ------------------------------------------------------------------------------

    fun updateSettings(transform: (SkySettings) -> SkySettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun toggleNightMode() = updateSettings { it.copy(nightMode = !it.nightMode) }
    fun toggleCamera() = updateSettings { it.copy(cameraBackground = !it.cameraBackground) }
    fun toggleManualMode() {
        if (!orientationProvider.isAvailable) return
        updateSettings { it.copy(manualMode = !it.manualMode) }
    }

    fun completeOnboarding() {
        updateSettings { it.copy(onboardingDone = true) }
        _screen.value = Screen.SKY
    }

    fun applyLanguage(choice: LanguageChoice, persist: Boolean = true) {
        if (persist) updateSettings { it.copy(language = choice) }
        val tags = when (choice) {
            LanguageChoice.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            LanguageChoice.FR -> LocaleListCompat.forLanguageTags("fr")
            LanguageChoice.EN -> LocaleListCompat.forLanguageTags("en")
        }
        if (AppCompatDelegate.getApplicationLocales() != tags) AppCompatDelegate.setApplicationLocales(tags)
        refreshLanguageDependentData()
    }

    /** À appeler quand la configuration (langue) change. */
    fun refreshLanguageDependentData() {
        engine?.setLabels(sceneLabels())
        catalog?.let { searchIndex = SearchIndex(it, currentLanguageTag()) }
    }

    private fun currentLanguageTag(): String = if (Locale.getDefault().language == "fr") "fr" else "en"

    private fun sceneLabels(): SceneLabels {
        val ctx: Context = getApplication()
        return SceneLabels(
            cardinalNorth = ctx.getString(R.string.cardinal_n),
            cardinalEast = ctx.getString(R.string.cardinal_e),
            cardinalSouth = ctx.getString(R.string.cardinal_s),
            cardinalWest = ctx.getString(R.string.cardinal_w),
            french = currentLanguageTag() == "fr",
        )
    }

    // ---- Lieu ----------------------------------------------------------------------------------

    fun refreshLocation() {
        viewModelScope.launch(Dispatchers.IO) { locationRepo.refresh(_settings.value) }
    }

    val hasLocationPermission: Boolean get() = locationRepo.hasPermission

    fun setManualLocation(name: String, latitude: Double, longitude: Double) = updateSettings {
        it.copy(locationMode = LocationMode.MANUAL, manualPlaceName = name, manualLatitude = latitude, manualLongitude = longitude)
    }

    fun setAutoLocation() = updateSettings { it.copy(locationMode = LocationMode.AUTO) }

    // ---- Recherche et guidage ------------------------------------------------------------------

    fun search(query: String): List<SearchResult> = searchIndex?.search(query) ?: emptyList()

    fun guideTo(result: SearchResult) {
        val target = when (val t = result.target) {
            is SearchTarget.StarTarget -> SkyTarget.Star(t.index)
            is SearchTarget.ConstellationTarget -> SkyTarget.Constellation(t.index)
            is SearchTarget.BodyTarget -> SkyTarget.Body(t.body)
        }
        guideTo(target)
    }

    fun guideTo(target: SkyTarget) {
        val e = engine ?: return
        e.setSearchTarget(target)
        if (_settings.value.manualMode) {
            e.targetEnu(target)?.let { d ->
                val h = fr.cartedueciel.astro.math.Enu.toHorizontal(fr.cartedueciel.astro.math.Vec3(d[0].toDouble(), d[1].toDouble(), d[2].toDouble()))
                e.lookAtManual(h.azimuthDeg, h.altitudeDeg)
            }
        }
        _sheet.value = null
    }

    fun clearGuidance() = engine?.setSearchTarget(null) ?: Unit

    fun details(target: SkyTarget): ObjectDetails? = engine?.details(target)

    // ---- Calibration ---------------------------------------------------------------------------

    fun calibrationCandidates(): List<Pair<SkyTarget.Star, String>> = engine?.brightNamedStarsAboveHorizon() ?: emptyList()

    /**
     * Cale la boussole : l'utilisateur a centré `star` au réticule. Le décalage ajouté est l'écart
     * entre l'azimut vrai de l'étoile et l'azimut actuellement visé. Renvoie le décalage total.
     */
    fun calibrateOn(star: SkyTarget.Star): Float? {
        val e = engine ?: return null
        val starAz = e.azimuthOf(star) ?: return null
        val lookAz = e.currentLookAzimuthDeg()
        var delta = starAz - lookAz
        while (delta > 180.0) delta -= 360.0
        while (delta < -180.0) delta += 360.0
        val total = (_settings.value.compassOffsetDeg + delta).toFloat()
        updateSettings { it.copy(compassOffsetDeg = total) }
        return total
    }

    fun resetCompassOffset() = updateSettings { it.copy(compassOffsetDeg = 0f) }

    // ---- Divers --------------------------------------------------------------------------------

    private fun vibrate() {
        val ctx: Context = getApplication()
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // Pas de vibreur : sans conséquence.
        }
    }

    private fun isTimeSetManually(ctx: Context): Boolean = try {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME, 1) == 0
    } catch (e: Exception) {
        false
    }

    companion object {
        /** Version lisible. */
        fun versionName(): String = BuildConfig.VERSION_NAME
    }
}
