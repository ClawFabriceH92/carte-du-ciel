package fr.cartedueciel.app.engine

import fr.cartedueciel.app.data.GridMode
import fr.cartedueciel.app.data.SkySettings
import fr.cartedueciel.astro.math.DeviceOrientation
import fr.cartedueciel.astro.math.Enu
import fr.cartedueciel.astro.math.Mat3
import fr.cartedueciel.astro.math.SkyCamera
import fr.cartedueciel.astro.math.StarColor
import fr.cartedueciel.astro.math.Vec3
import fr.cartedueciel.astro.sky.BodyState
import fr.cartedueciel.astro.sky.ConstellationFinder
import fr.cartedueciel.astro.sky.GeoPosition
import fr.cartedueciel.astro.sky.SkyEventCalculator
import fr.cartedueciel.astro.sky.SkyFrame
import fr.cartedueciel.astro.sky.SkyTime
import fr.cartedueciel.astro.sky.SolarBody
import fr.cartedueciel.astro.sky.SolarSystem
import fr.cartedueciel.astro.sky.Twilight
import fr.cartedueciel.catalog.SkyCatalog
import fr.cartedueciel.geomag.WorldMagneticModel
import fr.cartedueciel.render.LabelStyle
import fr.cartedueciel.render.LineBatch
import fr.cartedueciel.render.LineMode
import fr.cartedueciel.render.SceneSnapshot
import fr.cartedueciel.render.SkyLabel
import fr.cartedueciel.render.SpriteItem
import fr.cartedueciel.render.SpriteKind
import fr.cartedueciel.render.StarBuffer
import fr.cartedueciel.render.ViewState
import fr.cartedueciel.sensors.OrientationSample
import fr.cartedueciel.sensors.OrientationSource
import io.github.cosinekitty.astronomy.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Cœur de l'application : transforme le catalogue, la position, l'heure et l'orientation en
 * une scène pour le moteur de rendu, et fournit l'objet visé, le guidage et les fiches.
 *
 * Deux cadences (cahier des charges §5) :
 * - [tick] environ une fois par seconde : matrice J2000 → ENU, positions des étoiles, figures,
 *   grille, libellés ; corps du Système solaire une fois par minute ;
 * - [onOrientation] / [onManualGesture] à chaque échantillon : matrice de vue, objet au réticule,
 *   guidage.
 *
 * Toutes les méthodes sont synchronisées : elles sont appelées depuis des coroutines différentes
 * mais restent peu coûteuses.
 */
class SkyEngine(
    private val catalog: SkyCatalog,
    private val onScene: (SceneSnapshot) -> Unit,
    private val onView: (ViewState) -> Unit,
) {
    private val _hud = MutableStateFlow(HudState())
    val hud: StateFlow<HudState> = _hud

    /** Appelé (une fois par recherche) quand l'objet recherché passe au centre. */
    var onTargetCentered: ((SkyTarget) -> Unit)? = null

    // ---- Configuration -------------------------------------------------------------------------
    private var position: GeoPosition = GeoPosition.PARIS
    private var settings: SkySettings = SkySettings()
    private var labels: SceneLabels = SceneLabels("N", "E", "S", "O", true)
    private var displayRotation: Int = DeviceOrientation.ROTATION_0
    private var aspect: Double = 9.0 / 16.0
    /** Champ de vision le long du grand côté de l'écran (degrés). */
    private var baseFovDeg: Double = SkyCamera.DEFAULT_FOV_Y_DEG
    private var cameraFovLongSideDeg: Double? = null
    private var searchTarget: SkyTarget? = null
    private var centeredNotified = false

    // ---- Mode manuel ---------------------------------------------------------------------------
    private var manualAzDeg = 180.0
    private var manualAltDeg = 30.0
    private var manualFovDeg = SkyCamera.DEFAULT_FOV_Y_DEG

    // ---- État calculé --------------------------------------------------------------------------
    private var frame: SkyFrame = SkyFrame(Time(2000, 1, 1, 12, 0, 0.0), position)
    private var nowMillis: Long = 0L
    private val starEnu = FloatArray(catalog.starCount * 3)
    private var visibleStarCount = 0
    private var bodies: List<BodyState> = emptyList()
    private var bodyEnu: Array<Vec3> = emptyArray()
    private var lastBodyMillis = Long.MIN_VALUE
    private var declinationDeg = 0.0
    private var wmmValid = true
    private var lastDeclinationKey: String? = null
    private var viewMatrix: Mat3 = Mat3.IDENTITY
    private var lastSample: OrientationSample? = null
    private var lastHudMillis = 0L
    private var lastTarget: SkyTarget? = null

    // ---- Configuration externe -----------------------------------------------------------------

    @Synchronized fun setPosition(p: GeoPosition) { if (p != position) { position = p; rebuild() } }
    @Synchronized fun setSettings(s: SkySettings) { val changed = s != settings; settings = s; if (changed) rebuild() }
    @Synchronized fun setLabels(l: SceneLabels) { if (l != labels) { labels = l; rebuild() } }
    @Synchronized fun setDisplayRotation(r: Int) { displayRotation = r; updateView() }
    @Synchronized fun setViewport(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) { aspect = widthPx.toDouble() / heightPx; updateView() }
    }
    @Synchronized fun setCameraFov(longSideDeg: Double?) { cameraFovLongSideDeg = longSideDeg; updateView() }
    @Synchronized fun setSearchTarget(t: SkyTarget?) { searchTarget = t; centeredNotified = false; updateView() }
    @Synchronized fun searchTarget(): SkyTarget? = searchTarget

    // ---- Cadence « seconde » -------------------------------------------------------------------

    /** Recalcule le ciel pour l'instant donné (à appeler environ une fois par seconde). */
    @Synchronized fun tick(epochMillis: Long) {
        nowMillis = epochMillis
        rebuild()
    }

    private fun rebuild() {
        if (nowMillis == 0L) return
        val time = SkyTime.fromEpochMillis(nowMillis)
        frame = SkyFrame(time, position)
        updateDeclination()
        if (nowMillis - lastBodyMillis >= BODY_PERIOD_MS || bodies.isEmpty()) {
            bodies = SolarSystem.all(time, position)
            lastBodyMillis = nowMillis
        }
        bodyEnu = Array(bodies.size) { frame.toEnu(bodies[it].eqjDir, applyRefraction = true) }
        computeStarPositions()
        onScene(buildScene())
        updateView()
    }

    private fun updateDeclination() {
        val key = "%.2f,%.2f,%d".format(java.util.Locale.ROOT, position.latitudeDeg, position.longitudeDeg, nowMillis / DECLINATION_PERIOD_MS)
        if (key == lastDeclinationKey) return
        lastDeclinationKey = key
        val year = SkyTime.decimalYear(nowMillis)
        val model = WorldMagneticModel.wmm2025()
        wmmValid = model.isValidAt(year)
        declinationDeg = runCatching { model.declination(position.latitudeDeg, position.longitudeDeg, position.altitudeM / 1000.0, year) }.getOrDefault(0.0)
    }

    private fun computeStarPositions() {
        val m = frame.eqjToEnu.m
        val uv = catalog.stars.unitVectors
        val n = catalog.starCount
        for (i in 0 until n) {
            val x = uv[i * 3].toDouble(); val y = uv[i * 3 + 1].toDouble(); val z = uv[i * 3 + 2].toDouble()
            var ex = m[0] * x + m[1] * y + m[2] * z
            var ey = m[3] * x + m[4] * y + m[5] * z
            var ez = m[6] * x + m[7] * y + m[8] * z
            // Réfraction : on relève la hauteur apparente (formule d'Astronomy Engine), sensible seulement près de l'horizon.
            if (ez < 0.5 && ez > -0.1) {
                val alt = Math.toDegrees(kotlin.math.asin(ez.coerceIn(-1.0, 1.0)))
                val apparent = SkyFrame.apparentAltitudeDeg(alt)
                if (apparent != alt) {
                    val c0 = sqrt((1.0 - ez * ez).coerceAtLeast(1e-12))
                    val ca = cos(Math.toRadians(apparent))
                    val k = ca / c0
                    ex *= k; ey *= k; ez = sin(Math.toRadians(apparent))
                }
            }
            starEnu[i * 3] = ex.toFloat(); starEnu[i * 3 + 1] = ey.toFloat(); starEnu[i * 3 + 2] = ez.toFloat()
        }
        visibleStarCount = if (settings.showStars) catalog.countUpToMagnitude(settings.magnitudeLimit.toDouble()) else 0
    }

    private fun buildScene(): SceneSnapshot {
        val s = settings
        val limit = s.magnitudeLimit.toDouble()
        val stars = catalog.stars

        // Étoiles : taille et couleur selon magnitude et indice de couleur.
        val count = visibleStarCount
        val rgb = FloatArray(count * 3)
        val size = FloatArray(count)
        for (i in 0 until count) {
            val mag = stars.magnitude(i)
            val c = StarColor.rgb(stars.colorIndex(i))
            val brightness = (0.45 + (limit - mag) / 5.5).coerceIn(0.45, 1.0).toFloat()
            rgb[i * 3] = c[0] * brightness; rgb[i * 3 + 1] = c[1] * brightness; rgb[i * 3 + 2] = c[2] * brightness
            val base = 1.4 + (limit - mag) * 0.8
            val bonus = if (mag < 1.0) (1.0 - mag) * 0.8 else 0.0
            size[i] = (base + bonus).coerceIn(1.4, 12.0).toFloat()
        }
        val starBuffer = if (count > 0) StarBuffer(starEnu, rgb, size, count) else null

        val lines = ArrayList<LineBatch>()
        if (s.showLines) lines += constellationLines()
        if (s.showHorizon) lines += horizonLines()
        when (s.grid) {
            GridMode.AZIMUTHAL -> lines += azimuthalGrid()
            GridMode.EQUATORIAL -> lines += equatorialGrid()
            GridMode.NONE -> Unit
        }

        val sprites = ArrayList<SpriteItem>()
        val sceneLabels = ArrayList<SkyLabel>()
        if (s.showPlanets) {
            bodies.forEachIndexed { i, b ->
                val e = bodyEnu[i]
                val (kind, sizeDp, color) = bodyAppearance(b)
                sprites += SpriteItem(e.x.toFloat(), e.y.toFloat(), e.z.toFloat(), kind, sizeDp, color, b.phaseFraction.toFloat())
                if (s.showNames) {
                    val name = if (labels.french) b.body.nameFr else b.body.nameEn
                    val prio = if (b.body == SolarBody.SUN || b.body == SolarBody.MOON) -50f else -40f + b.magnitude.toFloat()
                    sceneLabels += SkyLabel(e.x.toFloat(), e.y.toFloat(), e.z.toFloat(), name, LabelStyle.BODY, prio)
                }
            }
        }
        if (s.showHorizon) {
            fun cardinal(az: Double, text: String) {
                val v = Enu.fromHorizontal(az, 0.0)
                sceneLabels += SkyLabel(v.x.toFloat(), v.y.toFloat(), v.z.toFloat(), text, LabelStyle.CARDINAL, -100f)
            }
            cardinal(0.0, labels.cardinalNorth); cardinal(90.0, labels.cardinalEast)
            cardinal(180.0, labels.cardinalSouth); cardinal(270.0, labels.cardinalWest)
        }
        if (s.showNames) {
            // Noms des constellations (position de libellé de d3-celestial).
            for (c in catalog.constellations) {
                val e = frame.toEnu(c.labelDirection, applyRefraction = true)
                val name = if (labels.french) c.nameFr else c.nameEn
                sceneLabels += SkyLabel(e.x.toFloat(), e.y.toFloat(), e.z.toFloat(), name, LabelStyle.CONSTELLATION, 5f + c.rank)
            }
            // Noms propres des étoiles les plus brillantes affichées.
            val nameLimit = minOf(limit, 2.5 + s.labelDensity * 4.0)
            for (i in 0 until count) {
                val id = stars.properNameId[i].toInt()
                if (id <= 0) continue
                val mag = stars.magnitude(i)
                if (mag > nameLimit) break
                sceneLabels += SkyLabel(starEnu[i * 3], starEnu[i * 3 + 1], starEnu[i * 3 + 2], catalog.strings[id], LabelStyle.STAR, mag.toFloat())
            }
        }

        val tw = frame.twilight
        val sky = when (tw) {
            Twilight.NIGHT -> floatArrayOf(0.00f, 0.00f, 0.03f, 1f)
            Twilight.ASTRONOMICAL -> floatArrayOf(0.01f, 0.02f, 0.08f, 1f)
            Twilight.NAUTICAL -> floatArrayOf(0.03f, 0.08f, 0.20f, 1f)
            Twilight.CIVIL -> floatArrayOf(0.12f, 0.25f, 0.45f, 1f)
            Twilight.DAY -> floatArrayOf(0.30f, 0.55f, 0.85f, 1f)
        }
        val ground = if (tw == Twilight.DAY || tw == Twilight.CIVIL) floatArrayOf(0.08f, 0.12f, 0.08f, 0.85f) else floatArrayOf(0.03f, 0.05f, 0.03f, 0.88f)
        val sun = frame.sunEnu
        return SceneSnapshot(
            stars = starBuffer,
            lines = lines,
            sprites = sprites,
            labels = sceneLabels,
            skyRgba = sky,
            groundRgba = ground,
            showGround = s.showGround,
            nightMode = s.nightMode,
            transparentBackground = s.cameraBackground && !s.nightMode,
            labelDensity = if (s.showNames) s.labelDensity else 0f,
            sunEnu = floatArrayOf(sun.x.toFloat(), sun.y.toFloat(), sun.z.toFloat()),
        )
    }

    private fun bodyAppearance(b: BodyState): Triple<SpriteKind, Float, FloatArray> = when (b.body) {
        SolarBody.SUN -> Triple(SpriteKind.SUN, 30f, floatArrayOf(1f, 1f, 1f))
        SolarBody.MOON -> Triple(SpriteKind.MOON, 26f, floatArrayOf(1f, 1f, 1f))
        else -> {
            val sizeDp = (10.5 - b.magnitude * 1.2).coerceIn(6.0, 13.0).toFloat()
            val color = when (b.body) {
                SolarBody.MERCURY -> floatArrayOf(0.85f, 0.82f, 0.75f)
                SolarBody.VENUS -> floatArrayOf(1f, 0.98f, 0.85f)
                SolarBody.MARS -> floatArrayOf(1f, 0.55f, 0.35f)
                SolarBody.JUPITER -> floatArrayOf(1f, 0.92f, 0.78f)
                SolarBody.SATURN -> floatArrayOf(1f, 0.93f, 0.68f)
                SolarBody.URANUS -> floatArrayOf(0.75f, 0.95f, 0.95f)
                SolarBody.NEPTUNE -> floatArrayOf(0.55f, 0.65f, 1f)
                else -> floatArrayOf(1f, 1f, 1f)
            }
            Triple(SpriteKind.PLANET, sizeDp, color)
        }
    }

    private fun constellationLines(): LineBatch {
        val segs = ArrayList<Float>(catalog.polylines.sumOf { it.starIndices.size } * 6)
        for (p in catalog.polylines) {
            for (k in 0 until p.starIndices.size - 1) {
                val a = p.starIndices[k] * 3; val b = p.starIndices[k + 1] * 3
                segs += starEnu[a]; segs += starEnu[a + 1]; segs += starEnu[a + 2]
                segs += starEnu[b]; segs += starEnu[b + 1]; segs += starEnu[b + 2]
            }
        }
        return LineBatch(segs.toFloatArray(), segs.size / 3, floatArrayOf(0.55f, 0.65f, 0.95f, 0.55f), 1.5f)
    }

    private fun horizonLines(): LineBatch {
        val v = ArrayList<Float>()
        fun add(az: Double, alt: Double) { val e = Enu.fromHorizontal(az, alt); v += e.x.toFloat(); v += e.y.toFloat(); v += e.z.toFloat() }
        val n = 120
        for (i in 0 until n) { add(360.0 * i / n, 0.0); add(360.0 * (i + 1) / n, 0.0) }
        for (az in 0 until 360 step 10) {
            val h = if (az % 90 == 0) 3.0 else if (az % 30 == 0) 1.6 else 0.8
            add(az.toDouble(), 0.0); add(az.toDouble(), h)
        }
        return LineBatch(v.toFloatArray(), v.size / 3, floatArrayOf(0.95f, 0.75f, 0.45f, 0.8f), 1.5f)
    }

    private fun azimuthalGrid(): LineBatch {
        val v = ArrayList<Float>()
        fun add(az: Double, alt: Double) { val e = Enu.fromHorizontal(az, alt); v += e.x.toFloat(); v += e.y.toFloat(); v += e.z.toFloat() }
        for (alt in listOf(-60, -30, 30, 60)) for (i in 0 until 72) { add(5.0 * i, alt.toDouble()); add(5.0 * (i + 1), alt.toDouble()) }
        for (az in 0 until 360 step 30) for (a in -85 until 85 step 5) { add(az.toDouble(), a.toDouble()); add(az.toDouble(), a + 5.0) }
        return LineBatch(v.toFloatArray(), v.size / 3, floatArrayOf(0.5f, 0.8f, 0.6f, 0.3f), 1f)
    }

    private fun equatorialGrid(): LineBatch {
        val v = ArrayList<Float>()
        fun add(raDeg: Double, decDeg: Double) {
            val e = frame.toEnu(fr.cartedueciel.astro.math.Equatorial.fromRaDec(Math.toRadians(raDeg), Math.toRadians(decDeg)), applyRefraction = false)
            v += e.x.toFloat(); v += e.y.toFloat(); v += e.z.toFloat()
        }
        for (dec in -75..75 step 15) for (i in 0 until 72) { add(5.0 * i, dec.toDouble()); add(5.0 * (i + 1), dec.toDouble()) }
        for (ra in 0 until 360 step 30) for (d in -85 until 85 step 5) { add(ra.toDouble(), d.toDouble()); add(ra.toDouble(), d + 5.0) }
        return LineBatch(v.toFloatArray(), v.size / 3, floatArrayOf(0.8f, 0.6f, 0.9f, 0.3f), 1f)
    }

    // ---- Cadence « orientation » ----------------------------------------------------------------

    @Synchronized fun onOrientation(sample: OrientationSample) {
        lastSample = sample
        if (!settings.manualMode) {
            viewMatrix = DeviceOrientation.viewMatrix(
                sample.deviceToWorld, displayRotation, declinationDeg, settings.compassOffsetDeg.toDouble(),
            )
        }
        updateView()
    }

    /**
     * Geste en mode manuel : déplacement en pixels, facteur de zoom. Le glissement tourne la vue
     * (la scène suit le doigt), le pincement change le champ.
     */
    @Synchronized fun onManualGesture(panXPx: Float, panYPx: Float, zoom: Float, viewportWidthPx: Int, viewportHeightPx: Int) {
        if (!settings.manualMode) return
        val fovY = currentFovYDeg()
        val fovX = Math.toDegrees(2.0 * atan(tan(Math.toRadians(fovY) / 2.0) * aspect))
        manualAzDeg -= panXPx / viewportWidthPx.coerceAtLeast(1) * fovX * cos(Math.toRadians(manualAltDeg)).coerceAtLeast(0.2)
        manualAltDeg += panYPx / viewportHeightPx.coerceAtLeast(1) * fovY
        manualAzDeg = ((manualAzDeg % 360.0) + 360.0) % 360.0
        manualAltDeg = manualAltDeg.coerceIn(-89.0, 89.0)
        if (zoom != 1f && zoom > 0f) manualFovDeg = (manualFovDeg / zoom).coerceIn(SkyCamera.MIN_FOV_Y_DEG, SkyCamera.MAX_FOV_Y_DEG)
        updateView()
    }

    /** Oriente la vue manuelle vers une direction donnée (par exemple l'objet recherché). */
    @Synchronized fun lookAtManual(azimuthDeg: Double, altitudeDeg: Double) {
        manualAzDeg = azimuthDeg; manualAltDeg = altitudeDeg.coerceIn(-89.0, 89.0)
        updateView()
    }

    private fun currentFovYDeg(): Double {
        val long = when {
            settings.manualMode -> manualFovDeg
            settings.cameraBackground && !settings.nightMode && cameraFovLongSideDeg != null -> cameraFovLongSideDeg!! * settings.cameraFovScale
            else -> baseFovDeg
        }.coerceIn(SkyCamera.MIN_FOV_Y_DEG, SkyCamera.MAX_FOV_Y_DEG)
        return if (aspect <= 1.0) long else Math.toDegrees(2.0 * atan(tan(Math.toRadians(long) / 2.0) / aspect))
    }

    private fun manualViewMatrix(): Mat3 {
        val look = Enu.fromHorizontal(manualAzDeg, manualAltDeg)
        var right = look cross Vec3.Z
        if (right.length < 1e-6) right = Enu.fromHorizontal(manualAzDeg + 90.0, 0.0)
        right = right.normalized()
        val up = right cross look
        return Mat3.fromRows(right, up, -look)
    }

    private fun updateView() {
        // Mode manuel, ou aucun échantillon de capteur reçu (démarrage, appareil sans capteur) : vue manuelle.
        if (settings.manualMode || lastSample == null) viewMatrix = manualViewMatrix()
        val fovY = currentFovYDeg()
        val camera = SkyCamera(viewMatrix, fovY, aspect)
        val look = camera.lookDirection

        // Objet au réticule.
        val pickRadiusRad = Math.toRadians((fovY * 0.045).coerceIn(1.0, 4.0))
        val picked = pick(look, pickRadiusRad)
        val target: SkyTarget? = picked ?: run {
            val eqj = frame.toEqj(look, removeRefraction = true)
            val abbr = runCatching { ConstellationFinder.abbreviationAt(eqj) }.getOrNull()
            abbr?.let { catalog.constellationByAbbreviation(it) }?.let { SkyTarget.Constellation(it.index) }
        }
        val highlight = picked?.let { targetEnu(it) }

        // Guidage.
        val st = searchTarget
        var guidance: GuidanceState? = null
        var guideEnu: FloatArray? = null
        if (st != null) {
            val dir = targetEnu(st)
            if (dir != null) {
                guideEnu = dir
                val d = Vec3(dir[0].toDouble(), dir[1].toDouble(), dir[2].toDouble())
                val distDeg = Math.toDegrees(look.angleTo(d))
                val c = viewMatrix * d
                val angle = atan2(c.x, c.y).toFloat()
                val onScreen = camera.project(d)?.isOnScreen == true
                val centered = distDeg < CENTERED_DEG
                if (centered && !centeredNotified) { centeredNotified = true; onTargetCentered?.invoke(st) }
                if (distDeg > CENTERED_DEG * 3) centeredNotified = false
                guidance = GuidanceState(st, targetName(st), angle, onScreen, distDeg, centered)
            }
        }

        onView(ViewState(viewMatrix.toGl4x4(), fovY.toFloat(), highlight, guideEnu))

        val now = System.currentTimeMillis()
        if (target != lastTarget || now - lastHudMillis >= HUD_PERIOD_MS) {
            lastTarget = target
            lastHudMillis = now
            val h = Enu.toHorizontal(look)
            val sample = lastSample
            _hud.value = HudState(
                target = target,
                targetName = target?.let { targetName(it) },
                targetSubtitle = target?.let { targetSubtitle(it) },
                lookAzimuthDeg = h.azimuthDeg,
                lookAltitudeDeg = h.altitudeDeg,
                compassQuality = sample?.compassQuality ?: fr.cartedueciel.sensors.CompassQuality.UNKNOWN,
                headingAccuracyDeg = sample?.headingAccuracyRad?.takeIf { it >= 0f }?.let { Math.toDegrees(it.toDouble()) },
                orientationSource = sample?.source ?: OrientationSource.NONE,
                guidance = guidance,
                declinationDeg = declinationDeg,
                wmmValid = wmmValid,
            )
        }
    }

    private fun pick(look: Vec3, radiusRad: Double): SkyTarget? {
        val cosLimit = cos(radiusRad)
        var best: SkyTarget? = null
        var bestDot = cosLimit
        if (settings.showPlanets) {
            for (i in bodies.indices) {
                val d = bodyEnu[i] dot look
                if (d > bestDot) { bestDot = d; best = SkyTarget.Body(bodies[i].body) }
            }
        }
        val lx = look.x.toFloat(); val ly = look.y.toFloat(); val lz = look.z.toFloat()
        var bestStarDot = bestDot.toFloat()
        var bestStar = -1
        for (i in 0 until visibleStarCount) {
            val d = starEnu[i * 3] * lx + starEnu[i * 3 + 1] * ly + starEnu[i * 3 + 2] * lz
            if (d > bestStarDot) { bestStarDot = d; bestStar = i }
        }
        if (bestStar >= 0) best = SkyTarget.Star(bestStar)
        return best
    }

    /** Direction ENU (réfraction comprise) d'une cible, ou null si inconnue. */
    @Synchronized fun targetEnu(t: SkyTarget): FloatArray? = when (t) {
        is SkyTarget.Star -> if (t.index in 0 until catalog.starCount) floatArrayOf(starEnu[t.index * 3], starEnu[t.index * 3 + 1], starEnu[t.index * 3 + 2]) else null
        is SkyTarget.Body -> bodies.indexOfFirst { it.body == t.body }.takeIf { it >= 0 }?.let { val e = bodyEnu[it]; floatArrayOf(e.x.toFloat(), e.y.toFloat(), e.z.toFloat()) }
        is SkyTarget.Constellation -> catalog.constellations.getOrNull(t.index)?.let { c ->
            val e = frame.toEnu(c.labelDirection, applyRefraction = true); floatArrayOf(e.x.toFloat(), e.y.toFloat(), e.z.toFloat())
        }
    }

    fun targetName(t: SkyTarget): String = when (t) {
        is SkyTarget.Star -> catalog.star(t.index).displayName
        is SkyTarget.Body -> if (labels.french) t.body.nameFr else t.body.nameEn
        is SkyTarget.Constellation -> catalog.constellations[t.index].let { if (labels.french) it.nameFr else it.nameEn }
    }

    private fun targetSubtitle(t: SkyTarget): String? = when (t) {
        is SkyTarget.Star -> {
            val s = catalog.star(t.index)
            listOfNotNull(s.secondaryDesignation, s.constellation?.let { if (labels.french) it.nameFr else it.nameEn }).joinToString(" · ").ifEmpty { null }
        }
        is SkyTarget.Body -> null
        is SkyTarget.Constellation -> catalog.constellations[t.index].latinName
    }

    // ---- Fiche objet ---------------------------------------------------------------------------

    @Synchronized fun details(t: SkyTarget): ObjectDetails {
        val time = frame.time
        return when (t) {
            is SkyTarget.Star -> {
                val s = catalog.star(t.index)
                val h = frame.horizontalOf(s.unitVector)
                ObjectDetails(
                    target = t, title = s.displayName, subtitle = s.secondaryDesignation, type = ObjectType.STAR,
                    constellationName = s.constellation?.let { if (labels.french) it.nameFr else it.nameEn },
                    magnitude = s.magnitude, distancePc = s.distancePc, distanceAu = null,
                    altitudeDeg = h.altitudeDeg, azimuthDeg = h.azimuthDeg,
                    events = SkyEventCalculator.forFixedPoint(s.unitVector, position, time),
                    moonPhase = null, phaseFraction = null, starCount = null,
                )
            }
            is SkyTarget.Body -> {
                val b = bodies.firstOrNull { it.body == t.body } ?: SolarSystem.state(t.body, time, position)
                val h = frame.horizontalOf(b.eqjDir)
                val abbr = runCatching { ConstellationFinder.abbreviationAt(b.eqjDir) }.getOrNull()
                ObjectDetails(
                    target = t, title = targetName(t), subtitle = null,
                    type = when (t.body) { SolarBody.SUN -> ObjectType.SUN; SolarBody.MOON -> ObjectType.MOON; else -> ObjectType.PLANET },
                    constellationName = abbr?.let { catalog.constellationByAbbreviation(it) }?.let { if (labels.french) it.nameFr else it.nameEn },
                    magnitude = b.magnitude, distancePc = null, distanceAu = b.distanceAu,
                    altitudeDeg = h.altitudeDeg, azimuthDeg = h.azimuthDeg,
                    events = SkyEventCalculator.forBody(t.body, position, time),
                    moonPhase = b.moonPhaseName, phaseFraction = if (t.body == SolarBody.MOON) b.phaseFraction else null,
                    starCount = null,
                )
            }
            is SkyTarget.Constellation -> {
                val c = catalog.constellations[t.index]
                val h = frame.horizontalOf(c.labelDirection)
                var n = 0
                val limit = catalog.countUpToMagnitude(6.5)
                for (i in 0 until limit) if (catalog.stars.constellation[i].toInt() == c.index) n++
                ObjectDetails(
                    target = t, title = if (labels.french) c.nameFr else c.nameEn,
                    subtitle = "${c.latinName} (${c.abbreviation}) · ${c.genitive}", type = ObjectType.CONSTELLATION,
                    constellationName = null, magnitude = null, distancePc = null, distanceAu = null,
                    altitudeDeg = h.altitudeDeg, azimuthDeg = h.azimuthDeg,
                    events = SkyEventCalculator.forFixedPoint(c.labelDirection, position, time),
                    moonPhase = null, phaseFraction = null, starCount = n,
                )
            }
        }
    }

    /** Azimut vrai de l'objet, pour le calage de la boussole sur une étoile connue. */
    @Synchronized fun azimuthOf(t: SkyTarget): Double? = targetEnu(t)?.let { Enu.toHorizontal(Vec3(it[0].toDouble(), it[1].toDouble(), it[2].toDouble())).azimuthDeg }

    /** Azimut actuellement visé. */
    @Synchronized fun currentLookAzimuthDeg(): Double = Enu.toHorizontal(DeviceOrientation.lookDirection(viewMatrix)).azimuthDeg

    /** Étoiles brillantes candidates pour le calage (nommées, magnitude < 2,5), avec leur hauteur actuelle. */
    @Synchronized fun brightNamedStarsAboveHorizon(): List<Pair<SkyTarget.Star, String>> {
        val out = ArrayList<Pair<SkyTarget.Star, String>>()
        val n = catalog.countUpToMagnitude(2.5)
        for (i in 0 until n) {
            if (catalog.stars.properNameId[i].toInt() <= 0) continue
            if (starEnu[i * 3 + 2] < 0.05f) continue
            out += SkyTarget.Star(i) to catalog.star(i).displayName
        }
        return out
    }

    companion object {
        const val BODY_PERIOD_MS = 60_000L
        const val DECLINATION_PERIOD_MS = 24L * 3600_000L
        const val HUD_PERIOD_MS = 100L
        const val CENTERED_DEG = 1.5

        /** Distance angulaire (degrés) entre deux directions unitaires. */
        fun angleDeg(a: FloatArray, b: FloatArray): Double {
            val d = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).toDouble().coerceIn(-1.0, 1.0)
            return Math.toDegrees(acos(d))
        }
    }
}
