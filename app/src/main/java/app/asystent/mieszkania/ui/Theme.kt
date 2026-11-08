package app.asystent.mieszkania.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.asystent.mieszkania.R

object Palette {
    /** Przełączane z zewnątrz (Hub / MainActivity). Odczyt w kompozycji odświeża interfejs. */
    var dark by mutableStateOf(true)

    val Ink: Color get() = if (dark) Color(0xFF0A1017) else Color(0xFFF3F5F8)
    val Panel: Color get() = if (dark) Color(0xFF141B23) else Color(0xFFFFFFFF)
    val PanelHigh: Color get() = if (dark) Color(0xFF1B2430) else Color(0xFFE9EDF2)
    val Line: Color get() = if (dark) Color(0xFF2A3441) else Color(0xFFD5DBE3)
    val Frost: Color get() = if (dark) Color(0xFFE8ECF1) else Color(0xFF121A24)
    val Slate: Color get() = if (dark) Color(0xFF8B97A6) else Color(0xFF5C6877)
    val Lamp: Color get() = if (dark) Color(0xFFF2B544) else Color(0xFFB7791F)
    val Signal: Color get() = if (dark) Color(0xFF4CC38A) else Color(0xFF1E8E5A)
    val Alert: Color get() = if (dark) Color(0xFFF0735F) else Color(0xFFC73E2A)
    val Violet: Color get() = if (dark) Color(0xFFB450FF) else Color(0xFF8A2BD6)

    /** Tekst i ikony na bursztynowym tle (zawsze ciemne). */
    val OnLamp = Color(0xFF0A1017)
}

val Plex = FontFamily(
    Font(R.font.plex_extralight, FontWeight.ExtraLight),
    Font(R.font.plex_light, FontWeight.Light),
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_bold, FontWeight.Bold),
)

private fun plexTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = Plex),
        displayMedium = b.displayMedium.copy(fontFamily = Plex),
        displaySmall = b.displaySmall.copy(fontFamily = Plex),
        headlineLarge = b.headlineLarge.copy(fontFamily = Plex),
        headlineMedium = b.headlineMedium.copy(fontFamily = Plex),
        headlineSmall = b.headlineSmall.copy(fontFamily = Plex),
        titleLarge = b.titleLarge.copy(fontFamily = Plex),
        titleMedium = b.titleMedium.copy(fontFamily = Plex),
        titleSmall = b.titleSmall.copy(fontFamily = Plex),
        bodyLarge = b.bodyLarge.copy(fontFamily = Plex),
        bodyMedium = b.bodyMedium.copy(fontFamily = Plex),
        bodySmall = b.bodySmall.copy(fontFamily = Plex),
        labelLarge = b.labelLarge.copy(fontFamily = Plex),
        labelMedium = b.labelMedium.copy(fontFamily = Plex),
        labelSmall = b.labelSmall.copy(fontFamily = Plex),
    )
}

@Composable
fun AsystentTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Palette.Lamp, onPrimary = Palette.OnLamp, secondary = Palette.Signal,
            background = Palette.Ink, onBackground = Palette.Frost,
            surface = Palette.Panel, onSurface = Palette.Frost,
            surfaceVariant = Palette.PanelHigh, onSurfaceVariant = Palette.Slate,
            outline = Palette.Line, error = Palette.Alert,
        )
    } else {
        lightColorScheme(
            primary = Palette.Lamp, onPrimary = Palette.OnLamp, secondary = Palette.Signal,
            background = Palette.Ink, onBackground = Palette.Frost,
            surface = Palette.Panel, onSurface = Palette.Frost,
            surfaceVariant = Palette.PanelHigh, onSurfaceVariant = Palette.Slate,
            outline = Palette.Line, error = Palette.Alert,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = plexTypography(), content = content)
}
