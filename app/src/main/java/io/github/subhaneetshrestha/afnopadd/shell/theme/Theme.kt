package io.github.subhaneetshrestha.afnopadd.shell.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's palette, taken from its icon: ink on warm paper with a vermilion accent, and in dark
 * mode paper-coloured ink on near-black. Text colours keep at least 4.5:1 against their surfaces
 * (the vermilion is deepened to #C2462A for that in light mode).
 */
object AfnopaddColors {
    val Ink = Color(0xFF20242C)
    val Paper = Color(0xFFFAF3E6)
    val Vermilion = Color(0xFFE4572E)
    val VermilionText = Color(0xFFC2462A)

    val NightInk = Color(0xFF1E2229)
    val NightPaper = Color(0xFFF3EBDD)
    val NightVermilion = Color(0xFFF26B3A)
}

private val LightColors = lightColorScheme(
    primary = AfnopaddColors.Ink,
    onPrimary = AfnopaddColors.Paper,
    primaryContainer = Color(0xFFE9E1D2),
    onPrimaryContainer = AfnopaddColors.Ink,
    secondary = AfnopaddColors.VermilionText,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADCD0),
    onSecondaryContainer = Color(0xFF5A1A0B),
    tertiary = AfnopaddColors.VermilionText,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFADCD0),
    onTertiaryContainer = Color(0xFF5A1A0B),
    background = AfnopaddColors.Paper,
    onBackground = AfnopaddColors.Ink,
    surface = AfnopaddColors.Paper,
    onSurface = AfnopaddColors.Ink,
    surfaceVariant = Color(0xFFEFE7D8),
    onSurfaceVariant = Color(0xFF4F4A42),
    surfaceTint = AfnopaddColors.Ink,
    outline = Color(0xFF8A8275),
    outlineVariant = Color(0xFFD6CDBD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7EFE1),
    surfaceContainer = Color(0xFFF2EADB),
    surfaceContainerHigh = Color(0xFFECE4D5),
    surfaceContainerHighest = Color(0xFFE6DECF),
    inverseSurface = AfnopaddColors.NightInk,
    inverseOnSurface = AfnopaddColors.NightPaper,
    inversePrimary = AfnopaddColors.NightPaper,
)

private val DarkColors = darkColorScheme(
    primary = AfnopaddColors.NightPaper,
    onPrimary = AfnopaddColors.NightInk,
    primaryContainer = Color(0xFF3A3F49),
    onPrimaryContainer = AfnopaddColors.NightPaper,
    secondary = AfnopaddColors.NightVermilion,
    onSecondary = Color(0xFF2A0E04),
    secondaryContainer = Color(0xFF6B2611),
    onSecondaryContainer = Color(0xFFFFDBCE),
    tertiary = AfnopaddColors.NightVermilion,
    onTertiary = Color(0xFF2A0E04),
    tertiaryContainer = Color(0xFF6B2611),
    onTertiaryContainer = Color(0xFFFFDBCE),
    background = AfnopaddColors.NightInk,
    onBackground = AfnopaddColors.NightPaper,
    surface = AfnopaddColors.NightInk,
    onSurface = AfnopaddColors.NightPaper,
    surfaceVariant = Color(0xFF2C313A),
    onSurfaceVariant = Color(0xFFCFC6B6),
    surfaceTint = AfnopaddColors.NightPaper,
    outline = Color(0xFF8E887E),
    outlineVariant = Color(0xFF444A54),
    surfaceContainerLowest = Color(0xFF171A20),
    surfaceContainerLow = Color(0xFF22262E),
    surfaceContainer = Color(0xFF262A32),
    surfaceContainerHigh = Color(0xFF2E333C),
    surfaceContainerHighest = Color(0xFF383D46),
    inverseSurface = AfnopaddColors.Paper,
    inverseOnSurface = AfnopaddColors.Ink,
    inversePrimary = AfnopaddColors.Ink,
)

/**
 * Material 3 theme following the system light/dark setting. Dynamic colour is deliberately off so
 * the app looks the same on every device and in screenshots. The canvas's own paper stays white:
 * colours drawn on it must look true, whatever the theme (app-shell spec decision 6).
 */
@Composable
fun AfnopaddTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
