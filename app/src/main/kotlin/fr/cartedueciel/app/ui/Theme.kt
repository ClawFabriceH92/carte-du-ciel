package fr.cartedueciel.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème jour : sombre, bleuté, contrasté (on observe le ciel, souvent dans la pénombre).
 * Thème nuit : exclusivement des rouges sur noir, y compris les surfaces « inverses » (snackbar),
 * pour qu'aucun élément blanc n'apparaisse (cahier des charges §3 et §9).
 */
private val DayColors = darkColorScheme(
    primary = Color(0xFF9BB7FF),
    onPrimary = Color(0xFF0B1026),
    primaryContainer = Color(0xFF1F2B55),
    onPrimaryContainer = Color(0xFFD6E0FF),
    secondary = Color(0xFFFFD27F),
    onSecondary = Color(0xFF2A1F00),
    secondaryContainer = Color(0xFF3A2E10),
    onSecondaryContainer = Color(0xFFFFE8B8),
    tertiary = Color(0xFF8FE3C8),
    onTertiary = Color(0xFF00382A),
    background = Color(0xFF070A14),
    onBackground = Color(0xFFE8ECF8),
    surface = Color(0xFF0E1222),
    onSurface = Color(0xFFE8ECF8),
    surfaceVariant = Color(0xFF1A2036),
    onSurfaceVariant = Color(0xFFB8C0D8),
    outline = Color(0xFF5B6480),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0000),
    inverseSurface = Color(0xFFE8ECF8),
    inverseOnSurface = Color(0xFF0E1222),
    inversePrimary = Color(0xFF2F4AA0),
    scrim = Color.Black,
)

private val NightColors = darkColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF4A0000),
    onPrimaryContainer = Color(0xFFFF8A80),
    secondary = Color(0xFFC62828),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF3B0000),
    onSecondaryContainer = Color(0xFFFF6E63),
    tertiary = Color(0xFFB71C1C),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2E0000),
    onTertiaryContainer = Color(0xFFFF5252),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE53935),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE53935),
    surfaceVariant = Color(0xFF1A0000),
    onSurfaceVariant = Color(0xFFC62828),
    surfaceTint = Color(0xFFE53935),
    outline = Color(0xFF7F0000),
    outlineVariant = Color(0xFF3B0000),
    error = Color(0xFFFF5252),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF3B0000),
    onErrorContainer = Color(0xFFFF8A80),
    inverseSurface = Color(0xFF2A0000),
    inverseOnSurface = Color(0xFFE53935),
    inversePrimary = Color(0xFF8E0000),
    scrim = Color.Black,
    surfaceContainer = Color(0xFF0D0000),
    surfaceContainerHigh = Color(0xFF140000),
    surfaceContainerHighest = Color(0xFF1C0000),
    surfaceContainerLow = Color(0xFF070000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceBright = Color(0xFF1C0000),
    surfaceDim = Color(0xFF000000),
)

@Composable
fun CarteDuCielTheme(nightMode: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (nightMode) NightColors else DayColors, content = content)
}

/** Couleurs de l'indicateur de boussole ; en mode nuit, nuances de rouge uniquement. */
object CompassColors {
    fun good(night: Boolean) = if (night) Color(0xFFFF5252) else Color(0xFF4CAF50)
    fun medium(night: Boolean) = if (night) Color(0xFFB71C1C) else Color(0xFFFF9800)
    fun poor(night: Boolean) = if (night) Color(0xFF7F0000) else Color(0xFFF44336)
    fun unknown(night: Boolean) = if (night) Color(0xFF5A0000) else Color(0xFF9E9E9E)
}
