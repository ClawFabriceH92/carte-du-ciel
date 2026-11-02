package fr.cartedueciel.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cartedueciel.app.R
import fr.cartedueciel.app.Screen
import fr.cartedueciel.app.Sheet
import fr.cartedueciel.app.SkyViewModel
import fr.cartedueciel.app.data.Cities
import fr.cartedueciel.app.data.DistanceUnit
import fr.cartedueciel.app.data.LanguageChoice
import fr.cartedueciel.app.data.LocationMode
import fr.cartedueciel.app.data.LocationSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SkyViewModel) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val hud by viewModel.hud.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.refreshLocation()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigate(Screen.SKY) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ---- Lieu ----
            SectionTitle(stringResource(R.string.settings_location))
            RadioRow(stringResource(R.string.settings_location_auto), s.locationMode == LocationMode.AUTO) { viewModel.setAutoLocation() }
            RadioRow(stringResource(R.string.settings_location_manual), s.locationMode == LocationMode.MANUAL) { viewModel.showSheet(Sheet.Location) }
            val placeText = when (location.source) {
                LocationSource.MANUAL -> location.name ?: Formatters.coordinate(location.position.latitudeDeg, location.position.longitudeDeg)
                LocationSource.DEVICE -> Formatters.coordinate(location.position.latitudeDeg, location.position.longitudeDeg)
                LocationSource.DEFAULT -> (location.name ?: "") + " · " + stringResource(R.string.warning_default_location)
            }
            Text(stringResource(R.string.settings_location_current, placeText), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.showSheet(Sheet.Location) }) { Text(stringResource(R.string.settings_location_choose)) }
                if (!viewModel.hasLocationPermission) {
                    OutlinedButton(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                        Text(stringResource(R.string.settings_location_permission))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---- Boussole ----
            SectionTitle(stringResource(R.string.settings_compass))
            Text(stringResource(R.string.settings_compass_offset, Formatters.signedDegrees(s.compassOffsetDeg.toDouble())), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_compass_declination, Formatters.signedDegrees(hud.declinationDeg)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.showSheet(Sheet.Calibrate) }, enabled = viewModel.orientationAvailable) { Text(stringResource(R.string.settings_compass_calibrate)) }
                TextButton(onClick = viewModel::resetCompassOffset, enabled = s.compassOffsetDeg != 0f) { Text(stringResource(R.string.settings_compass_reset)) }
            }
            Text(stringResource(R.string.settings_smoothing), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Slider(value = s.smoothing, onValueChange = { v -> viewModel.updateSettings { it.copy(smoothing = v) } }, valueRange = 0f..1f)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---- Unités ----
            SectionTitle(stringResource(R.string.settings_units))
            val units = listOf(DistanceUnit.LIGHT_YEARS to R.string.units_light_years, DistanceUnit.PARSECS to R.string.units_parsecs)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                units.forEachIndexed { i, (u, label) ->
                    SegmentedButton(selected = s.distanceUnit == u, onClick = { viewModel.updateSettings { it.copy(distanceUnit = u) } }, shape = SegmentedButtonDefaults.itemShape(i, units.size)) { Text(stringResource(label)) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---- Langue ----
            SectionTitle(stringResource(R.string.settings_language))
            val langs = listOf(LanguageChoice.SYSTEM to R.string.language_system, LanguageChoice.FR to R.string.language_fr, LanguageChoice.EN to R.string.language_en)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                langs.forEachIndexed { i, (l, label) ->
                    SegmentedButton(selected = s.language == l, onClick = { viewModel.applyLanguage(l) }, shape = SegmentedButtonDefaults.itemShape(i, langs.size)) { Text(stringResource(label)) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---- Caméra ----
            Text(stringResource(R.string.settings_camera_fov, Formatters.number(s.cameraFovScale.toDouble(), 2)), style = MaterialTheme.typography.bodyMedium)
            Slider(value = s.cameraFovScale, onValueChange = { v -> viewModel.updateSettings { it.copy(cameraFovScale = v) } }, valueRange = 0.6f..1.5f)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ListItem(headlineContent = { Text(stringResource(R.string.settings_about)) }, modifier = Modifier.clickable { viewModel.navigate(Screen.ABOUT) })
            ListItem(headlineContent = { Text(stringResource(R.string.settings_onboarding_again)) }, modifier = Modifier.clickable { viewModel.navigate(Screen.ONBOARDING) })
            Spacer(Modifier.height(24.dp))
        }
    }
    @Suppress("UNUSED_VARIABLE") val unusedContext = context
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Choix d'une ville ou saisie de coordonnées. */
@Composable
fun LocationDialog(viewModel: SkyViewModel) {
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = viewModel::dismissSheet,
        title = { Text(stringResource(R.string.city_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.city_custom), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(lat, { lat = it }, label = { Text(stringResource(R.string.city_latitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(lon, { lon = it }, label = { Text(stringResource(R.string.city_longitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                if (error) Text(stringResource(R.string.city_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        val la = lat.replace(',', '.').toDoubleOrNull()
                        val lo = lon.replace(',', '.').toDoubleOrNull()
                        if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                            error = true
                        } else {
                            viewModel.setManualLocation(Formatters.coordinate(la, lo), la, lo)
                            viewModel.dismissSheet()
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text(stringResource(R.string.action_validate)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.height(260.dp)) {
                    items(Cities.all) { city ->
                        ListItem(
                            headlineContent = { Text(city.name) },
                            supportingContent = { Text(Formatters.coordinate(city.latitude, city.longitude)) },
                            modifier = Modifier.clickable {
                                viewModel.setManualLocation(city.name, city.latitude, city.longitude)
                                viewModel.dismissSheet()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::dismissSheet) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Calage de la boussole sur une étoile connue centrée au réticule. */
@Composable
fun CalibrationDialog(viewModel: SkyViewModel) {
    val context = LocalContext.current
    val candidates = remember { viewModel.calibrationCandidates() }
    AlertDialog(
        onDismissRequest = viewModel::dismissSheet,
        title = { Text(stringResource(R.string.calibrate_title)) },
        text = {
            Column {
                Text(stringResource(R.string.calibrate_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.calibrate_choose), style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.height(240.dp)) {
                    items(candidates) { (star, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            modifier = Modifier.clickable {
                                val total = viewModel.calibrateOn(star)
                                if (total != null) viewModel.showMessage(context.getString(R.string.calibrate_done, Formatters.signedDegrees(total.toDouble())))
                                viewModel.dismissSheet()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::dismissSheet) { Text(stringResource(R.string.action_cancel)) } },
    )
}
