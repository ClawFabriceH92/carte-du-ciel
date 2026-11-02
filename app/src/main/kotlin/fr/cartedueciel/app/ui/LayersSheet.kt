package fr.cartedueciel.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cartedueciel.app.R
import fr.cartedueciel.app.SkyViewModel
import fr.cartedueciel.app.data.GridMode
import fr.cartedueciel.app.data.SkySettings

/** Couches : interrupteurs, grille, magnitude limite avec préréglages, densité des noms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(viewModel: SkyViewModel) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = viewModel::dismissSheet, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.layers_title), style = MaterialTheme.typography.titleLarge)
            SwitchRow(stringResource(R.string.layer_stars), s.showStars) { v -> viewModel.updateSettings { it.copy(showStars = v) } }
            SwitchRow(stringResource(R.string.layer_lines), s.showLines) { v -> viewModel.updateSettings { it.copy(showLines = v) } }
            SwitchRow(stringResource(R.string.layer_names), s.showNames) { v -> viewModel.updateSettings { it.copy(showNames = v) } }
            SwitchRow(stringResource(R.string.layer_planets), s.showPlanets) { v -> viewModel.updateSettings { it.copy(showPlanets = v) } }
            SwitchRow(stringResource(R.string.layer_horizon), s.showHorizon) { v -> viewModel.updateSettings { it.copy(showHorizon = v) } }
            SwitchRow(stringResource(R.string.layer_ground), s.showGround) { v -> viewModel.updateSettings { it.copy(showGround = v) } }

            Text(stringResource(R.string.layer_grid), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            val grids = listOf(GridMode.NONE to R.string.grid_none, GridMode.AZIMUTHAL to R.string.grid_azimuthal, GridMode.EQUATORIAL to R.string.grid_equatorial)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                grids.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = s.grid == mode,
                        onClick = { viewModel.updateSettings { it.copy(grid = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = grids.size),
                    ) { Text(stringResource(label)) }
                }
            }

            Text(
                stringResource(R.string.layer_magnitude, Formatters.magnitude(s.magnitudeLimit.toDouble())),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            Slider(
                value = s.magnitudeLimit,
                onValueChange = { v -> viewModel.updateSettings { it.copy(magnitudeLimit = v) } },
                valueRange = SkySettings.MIN_MAGNITUDE..SkySettings.MAX_MAGNITUDE,
                steps = 10,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip(stringResource(R.string.preset_city), s.magnitudeLimit, SkySettings.PRESET_CITY, viewModel)
                PresetChip(stringResource(R.string.preset_country), s.magnitudeLimit, SkySettings.PRESET_COUNTRY, viewModel)
                PresetChip(stringResource(R.string.preset_dark), s.magnitudeLimit, SkySettings.PRESET_DARK, viewModel)
            }

            Text(stringResource(R.string.layer_label_density), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Slider(
                value = s.labelDensity,
                onValueChange = { v -> viewModel.updateSettings { it.copy(labelDensity = v) } },
                valueRange = 0f..1f,
            )
        }
    }
}

@Composable
private fun PresetChip(label: String, current: Float, value: Float, viewModel: SkyViewModel) {
    FilterChip(
        selected = kotlin.math.abs(current - value) < 0.05f,
        onClick = { viewModel.updateSettings { it.copy(magnitudeLimit = value) } },
        label = { Text(label) },
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
