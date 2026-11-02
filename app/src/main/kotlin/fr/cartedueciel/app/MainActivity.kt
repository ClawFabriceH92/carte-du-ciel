package fr.cartedueciel.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.cartedueciel.app.ui.CarteDuCielApp
import fr.cartedueciel.sensors.DisplayRotation
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: SkyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // L'écran reste allumé pendant l'observation.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Capteurs et horloge actifs seulement quand l'activité est visible (cahier des charges §8).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onDisplayRotation(DisplayRotation.current(this@MainActivity))
                viewModel.start()
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    viewModel.stop()
                }
            }
        }

        setContent {
            CarteDuCielApp(
                viewModel = viewModel,
                setImmersive = { immersive -> setImmersive(immersive) },
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.onDisplayRotation(DisplayRotation.current(this))
        viewModel.refreshLanguageDependentData()
    }

    private fun setImmersive(immersive: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
