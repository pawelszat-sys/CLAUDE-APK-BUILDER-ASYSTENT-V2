package app.asystent.mieszkania.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.asystent.mieszkania.R

object Palette {
    val Ink = Color(0xFF0A1017)
    val Panel = Color(0xFF141B23)
    val PanelHigh = Color(0xFF1B2430)
    val Line = Color(0xFF2A3441)
    val Frost = Color(0xFFE8ECF1)
    val Slate = Color(0xFF8B97A6)
    val Lamp = Color(0xFFF2B544)
    val Signal = Color(0xFF4CC38A)
    val Alert = Color(0xFFF0735F)
    val Violet = Color(0xFFB450FF)
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
fun AsystentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Lamp,
            onPrimary = Palette.Ink,
            secondary = Palette.Signal,
            background = Palette.Ink,
            onBackground = Palette.Frost,
            surface = Palette.Panel,
            onSurface = Palette.Frost,
            surfaceVariant = Palette.PanelHigh,
            onSurfaceVariant = Palette.Slate,
            outline = Palette.Line,
            error = Palette.Alert,
        ),
        typography = plexTypography(),
        content = content,
    )
}
