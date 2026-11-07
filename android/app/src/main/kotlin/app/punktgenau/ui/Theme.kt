package app.punktgenau.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFE8650A)
private val OrangeDark = Color(0xFFFF8A3D)
private val Anthracite = Color(0xFF1F2326)
private val DarkSurface = Color(0xFF2A3035)

val PositiveGreen = Color(0xFF1E8E3E)
val NegativeRed = Color(0xFFC5221F)

private val Light = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    background = Color(0xFFFAFAF9),
    surface = Color(0xFFFAFAF9),
    onBackground = Anthracite,
    onSurface = Anthracite,
)

private val Dark = darkColorScheme(
    primary = OrangeDark,
    onPrimary = Color(0xFF2A1400),
    background = Anthracite,
    surface = Anthracite,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = Color(0xFFD5D9DC),
    outline = Color(0xFF9AA3A9),
    secondaryContainer = Color(0xFF4A3524),
    onSecondaryContainer = Color(0xFFFFE2CC),
    onBackground = Color(0xFFF5F5F3),
    onSurface = Color(0xFFF5F5F3),
)

@Composable
fun PunktgenauTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
