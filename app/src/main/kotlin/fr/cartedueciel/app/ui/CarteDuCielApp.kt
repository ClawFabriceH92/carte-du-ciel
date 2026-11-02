package fr.cartedueciel.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cartedueciel.app.Screen
import fr.cartedueciel.app.Sheet
import fr.cartedueciel.app.SkyViewModel

@Composable
fun CarteDuCielApp(viewModel: SkyViewModel, setImmersive: (Boolean) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    CarteDuCielTheme(nightMode = settings.nightMode) {
        LaunchedEffect(screen) { setImmersive(screen == Screen.SKY) }
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(message) {
            val m = message ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(m)
            viewModel.showMessage(null)
        }

        BackHandler(enabled = sheet != null || screen == Screen.SETTINGS || screen == Screen.ABOUT) {
            when {
                sheet != null -> viewModel.dismissSheet()
                screen == Screen.ABOUT -> viewModel.navigate(Screen.SETTINGS)
                else -> viewModel.navigate(Screen.SKY)
            }
        }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (screen) {
                Screen.LOADING -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                Screen.ONBOARDING -> OnboardingScreen(viewModel)
                Screen.SKY -> SkyScreen(viewModel)
                Screen.SETTINGS -> SettingsScreen(viewModel)
                Screen.ABOUT -> AboutScreen(viewModel)
            }
            when (val s = sheet) {
                null -> Unit
                Sheet.Search -> SearchSheet(viewModel)
                Sheet.Layers -> LayersSheet(viewModel)
                is Sheet.Object -> ObjectSheet(viewModel, s.details)
                Sheet.Calibrate -> CalibrationDialog(viewModel)
                Sheet.Location -> LocationDialog(viewModel)
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}
