package app.punktgenau.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Darstellung: hell ist der Standard, dunkel nur auf Wunsch (oder nach System). */
enum class ThemeMode(val label: String) { LIGHT("Hell"), SYSTEM("System"), DARK("Dunkel") }

private val Orange = Color(0xFFE8650A)
private val OrangeSoft = Color(0xFFFFEDE0)
private val Ink = Color(0xFF1B1F23)
private val InkSoft = Color(0xFF5B646C)
private val Paper = Color(0xFFF4F3F0)

val PositiveGreen = Color(0xFF14804A)
val NegativeRed = Color(0xFFC62828)
val NeutralGrey = Color(0xFF6B747C)

private val Light = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeSoft,
    onPrimaryContainer = Color(0xFF5A2300),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECEAE5),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFBDB9B1),
    outlineVariant = Color(0xFFE6E3DD),
    surfaceContainerLow = Color(0xFFFAFAF8),
    surfaceContainer = Color(0xFFF1F0EC),
    secondaryContainer = OrangeSoft,
    onSecondaryContainer = Color(0xFF5A2300),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color(0xFF2A1400),
    primaryContainer = Color(0xFF4A2A12),
    onPrimaryContainer = Color(0xFFFFE2CC),
    background = Color(0xFF15181B),
    onBackground = Color(0xFFF2F2F0),
    surface = Color(0xFF1F2327),
    onSurface = Color(0xFFF2F2F0),
    surfaceVariant = Color(0xFF2B3035),
    onSurfaceVariant = Color(0xFFC9CED2),
    outline = Color(0xFF8A939A),
    outlineVariant = Color(0xFF2F353B),
    surfaceContainerLow = Color(0xFF1A1E22),
    surfaceContainer = Color(0xFF232830),
    secondaryContainer = Color(0xFF4A2A12),
    onSecondaryContainer = Color(0xFFFFE2CC),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun PunktgenauTheme(mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
}
