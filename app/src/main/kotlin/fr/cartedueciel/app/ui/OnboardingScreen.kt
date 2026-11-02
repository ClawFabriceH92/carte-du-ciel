package fr.cartedueciel.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cartedueciel.app.R
import fr.cartedueciel.app.Sheet
import fr.cartedueciel.app.SkyViewModel
import fr.cartedueciel.sensors.CompassQuality

/** Premier lancement : principe, localisation, calibration de la boussole. */
@Composable
fun OnboardingScreen(viewModel: SkyViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val hud by viewModel.hud.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.refreshLocation()
        step = 2
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            0 -> {
                AppIcons.Compass(Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.onboarding_principle), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_next)) }
            }
            1 -> {
                Text(stringResource(R.string.onboarding_location_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.onboarding_location_text), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_location_allow))
                }
                OutlinedButton(onClick = { viewModel.showSheet(Sheet.Location); step = 2 }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_location_manual))
                }
                TextButton(onClick = { step = 2 }) { Text(stringResource(R.string.action_skip)) }
            }
            else -> {
                Text(stringResource(R.string.onboarding_compass_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                if (viewModel.orientationAvailable) {
                    Text(stringResource(R.string.onboarding_compass_text), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val color = when (hud.compassQuality) {
                            CompassQuality.GOOD -> CompassColors.good(settings.nightMode)
                            CompassQuality.MEDIUM -> CompassColors.medium(settings.nightMode)
                            CompassQuality.POOR -> CompassColors.poor(settings.nightMode)
                            CompassQuality.UNKNOWN -> CompassColors.unknown(settings.nightMode)
                        }
                        AppIcons.Compass(Modifier.size(40.dp), tint = color)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            when (hud.compassQuality) {
                                CompassQuality.GOOD -> stringResource(R.string.compass_good)
                                CompassQuality.MEDIUM -> stringResource(R.string.compass_medium)
                                CompassQuality.POOR -> stringResource(R.string.compass_poor)
                                CompassQuality.UNKNOWN -> stringResource(R.string.compass_unknown)
                            },
                        )
                    }
                } else {
                    Text(stringResource(R.string.onboarding_no_sensor), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = viewModel::completeOnboarding, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_start)) }
            }
        }
    }
}
