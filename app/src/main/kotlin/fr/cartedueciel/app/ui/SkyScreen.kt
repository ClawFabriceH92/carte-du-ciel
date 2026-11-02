package fr.cartedueciel.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cartedueciel.app.R
import fr.cartedueciel.app.Screen
import fr.cartedueciel.app.Sheet
import fr.cartedueciel.app.SkyViewModel
import fr.cartedueciel.app.data.LocationSource
import fr.cartedueciel.app.engine.GuidanceState
import fr.cartedueciel.app.engine.HudState
import fr.cartedueciel.camera.CameraPreview
import fr.cartedueciel.render.SkyGLSurfaceView
import fr.cartedueciel.sensors.CompassQuality
import fr.cartedueciel.sensors.OrientationSource
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SkyScreen(viewModel: SkyViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hud by viewModel.hud.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val cameraGranted by viewModel.cameraPermission.collectAsStateWithLifecycle()
    val timeWarning by viewModel.manualTimeWarning.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onCameraPermission(granted)
        if (!granted) {
            viewModel.showMessage(context.getString(R.string.camera_permission_denied))
            viewModel.updateSettings { it.copy(cameraBackground = false) }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.onCameraPermission(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraActive = settings.cameraBackground && !settings.nightMode
    LaunchedEffect(cameraActive, cameraGranted) {
        if (cameraActive && !cameraGranted) cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize()) {
        if (cameraActive && cameraGranted) {
            CameraPreview(Modifier.fillMaxSize()) { fov -> viewModel.onCameraFov(fov?.longSideDeg) }
        }
        SkyGlView(viewModel, Modifier.fillMaxSize())

        // Gestes du mode manuel (couche transparente au-dessus de la vue OpenGL).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(settings.manualMode) {
                    if (settings.manualMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            viewModel.onManualGesture(pan.x, pan.y, zoom, size.width, size.height)
                        }
                    }
                },
        )

        Reticle(Modifier.align(Alignment.Center))

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusChip(hud, settings.manualMode, settings.nightMode, onClick = {
                if (hud.compassQuality == CompassQuality.POOR) viewModel.showMessage(context.getString(R.string.compass_poor))
            })
            if (timeWarning) WarningLine(stringResource(R.string.warning_manual_time))
            if (location.source == LocationSource.DEFAULT) {
                WarningLine(stringResource(R.string.warning_default_location), onClick = { viewModel.navigate(Screen.SETTINGS) })
            }
            if (!hud.wmmValid) WarningLine(stringResource(R.string.warning_wmm_expired))
        }

        hud.guidance?.let { g -> GuidanceOverlay(g, onStop = viewModel::clearGuidance) }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 88.dp),
        ) {
            TargetCard(hud, onClick = viewModel::openCurrentTarget)
        }

        FabColumn(
            viewModel = viewModel,
            nightMode = settings.nightMode,
            cameraOn = settings.cameraBackground,
            manualMode = settings.manualMode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(12.dp),
        )
    }
}

@Composable
private fun SkyGlView(viewModel: SkyViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember { SkyGLSurfaceView(context) }
    DisposableEffect(lifecycleOwner, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.attachRenderer(view)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.detachRenderer(view)
        }
    }
    AndroidView(
        factory = { view },
        modifier = modifier.onSizeChanged { viewModel.onViewportChanged(it.width, it.height) },
    )
}

@Composable
private fun Reticle(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
    Canvas(modifier.size(56.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.width * 0.32f
        drawCircle(color, radius = r, center = c, style = Stroke(width = 1.5.dp.toPx()))
        val gap = r * 0.55f
        val len = r * 1.35f
        val w = 1.5.dp.toPx()
        drawLine(color, Offset(c.x - len, c.y), Offset(c.x - gap, c.y), w)
        drawLine(color, Offset(c.x + gap, c.y), Offset(c.x + len, c.y), w)
        drawLine(color, Offset(c.x, c.y - len), Offset(c.x, c.y - gap), w)
        drawLine(color, Offset(c.x, c.y + gap), Offset(c.x, c.y + len), w)
    }
}

@Composable
private fun StatusChip(hud: HudState, manualMode: Boolean, nightMode: Boolean, onClick: () -> Unit) {
    val (text, color) = when {
        hud.orientationSource == OrientationSource.NONE && !manualMode -> stringResource(R.string.compass_no_sensor) to CompassColors.unknown(nightMode)
        manualMode -> stringResource(R.string.compass_manual) to CompassColors.unknown(nightMode)
        hud.compassQuality == CompassQuality.GOOD -> stringResource(R.string.compass_good) to CompassColors.good(nightMode)
        hud.compassQuality == CompassQuality.MEDIUM -> stringResource(R.string.compass_medium) to CompassColors.medium(nightMode)
        hud.compassQuality == CompassQuality.POOR -> stringResource(R.string.compass_poor) to CompassColors.poor(nightMode)
        else -> stringResource(R.string.compass_unknown) to CompassColors.unknown(nightMode)
    }
    val detail = buildString {
        hud.headingAccuracyDeg?.let { append(" ±").append(Formatters.degrees(it, 0)) }
        if (hud.orientationSource == OrientationSource.GEOMAGNETIC_ROTATION_VECTOR) append(" · ").append(stringResource(R.string.compass_geomagnetic))
    }
    AssistChip(
        onClick = onClick,
        label = { Text(text + detail, maxLines = 1) },
        leadingIcon = { AppIcons.Compass(Modifier.size(18.dp), tint = color) },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
    )
}

@Composable
private fun WarningLine(text: String, onClick: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .padding(top = 6.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TargetCard(hud: HudState, onClick: () -> Unit) {
    val name = hud.targetName ?: stringResource(R.string.object_unknown)
    val direction = stringResource(R.string.target_direction, Formatters.degrees(hud.lookAzimuthDeg, 0), Formatters.degrees(hud.lookAltitudeDeg, 0))
    val announcement = stringResource(R.string.target_announcement, name, direction)
    Surface(
        onClick = onClick,
        enabled = hud.target != null,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            hud.targetSubtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            Text(direction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuidanceOverlay(g: GuidanceState, onStop: () -> Unit) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        if (!g.onScreen) {
            // Flèche sur un cercle autour du réticule, dans la direction de l'objet.
            val radiusDp = 110.dp
            val angle = g.arrowAngleRad
            val dx = with(density) { (radiusDp.toPx() * sin(angle)).toDp() }
            val dy = with(density) { (-radiusDp.toPx() * cos(angle)).toDp() }
            AppIcons.Arrow(
                modifier = Modifier.align(Alignment.Center).offset(x = dx, y = dy),
                tint = MaterialTheme.colorScheme.secondary,
                angleDeg = Math.toDegrees(angle.toDouble()).toFloat(),
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.Center).offset(y = 150.dp),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val text = if (g.centered) stringResource(R.string.guidance_centered, g.name)
                else stringResource(R.string.guidance_distance, g.name, Formatters.degrees(g.distanceDeg, 0))
                Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.guidance_stop),
                    modifier = Modifier.size(20.dp).clickable(onClick = onStop),
                )
            }
        }
    }
}

@Composable
private fun FabColumn(viewModel: SkyViewModel, nightMode: Boolean, cameraOn: Boolean, manualMode: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
        SmallFloatingActionButton(onClick = { viewModel.showSheet(Sheet.Search) }) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.fab_search))
        }
        SmallFloatingActionButton(onClick = { viewModel.showSheet(Sheet.Layers) }) {
            Box(Modifier.semantics { contentDescription = context.getString(R.string.fab_layers) }) { AppIcons.Layers() }
        }
        SmallFloatingActionButton(onClick = viewModel::toggleNightMode) {
            val desc = stringResource(if (nightMode) R.string.fab_night_off else R.string.fab_night_on)
            Box(Modifier.semantics { contentDescription = desc }) { AppIcons.Night() }
        }
        SmallFloatingActionButton(onClick = {
            if (nightMode) viewModel.showMessage(context.getString(R.string.camera_night_disabled)) else viewModel.toggleCamera()
        }) {
            val desc = stringResource(if (cameraOn) R.string.fab_camera_off else R.string.fab_camera_on)
            Box(Modifier.semantics { contentDescription = desc }) {
                AppIcons.Camera(tint = if (cameraOn && !nightMode) MaterialTheme.colorScheme.secondary else androidx.compose.material3.LocalContentColor.current)
            }
        }
        if (viewModel.orientationAvailable) {
            SmallFloatingActionButton(onClick = viewModel::toggleManualMode) {
                val desc = stringResource(if (manualMode) R.string.fab_manual_off else R.string.fab_manual_on)
                Box(Modifier.semantics { contentDescription = desc }) {
                    AppIcons.Manual(tint = if (manualMode) MaterialTheme.colorScheme.secondary else androidx.compose.material3.LocalContentColor.current)
                }
            }
        }
        SmallFloatingActionButton(onClick = { viewModel.navigate(Screen.SETTINGS) }) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.fab_settings))
        }
        Spacer(Modifier.height(0.dp))
    }
}

@Suppress("unused")
private val transparent = Color.Transparent
