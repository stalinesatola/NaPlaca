package ao.naplaca.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ao.naplaca.app.data.local.ThemeMode

val Green = Color(0xFF0B8A4A)
val GreenDark = Color(0xFF075C31)
val Yellow = Color(0xFFF5C518)
val LiveRed = Color(0xFFE53935)

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F1D7),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF6B5B00),
    secondaryContainer = Color(0xFFFFE57A),
    onSecondaryContainer = Color(0xFF211B00),
    tertiary = LiveRed,
    background = Color(0xFFF8FAF7),
    surface = Color(0xFFF8FAF7),
    surfaceVariant = Color(0xFFE0E7E1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FDBA3),
    onPrimary = Color(0xFF00391C),
    primaryContainer = GreenDark,
    onPrimaryContainer = Color(0xFFC8F1D7),
    secondary = Yellow,
    onSecondary = Color(0xFF221B00),
    secondaryContainer = Color(0xFF524600),
    onSecondaryContainer = Color(0xFFFFE57A),
    tertiary = Color(0xFFFF8A80),
    background = Color(0xFF0E1512),
    surface = Color(0xFF0E1512),
    surfaceVariant = Color(0xFF26302A),
)

@Composable
fun NaPlacaTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
