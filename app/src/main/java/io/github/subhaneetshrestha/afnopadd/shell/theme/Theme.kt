package io.github.subhaneetshrestha.afnopadd.shell.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Placeholder brand colour until the design pass listed as a release blocker in SPEC.md. */
private val BrandGreen = Color(0xFF1E6F5C)
private val BrandGreenLight = Color(0xFF7FD1B9)

private val LightColors = lightColorScheme(primary = BrandGreen)
private val DarkColors = darkColorScheme(primary = BrandGreenLight)

/**
 * Material 3 theme following the system light/dark setting. Dynamic colour is deliberately off so
 * the app looks the same on every device and in screenshots.
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
