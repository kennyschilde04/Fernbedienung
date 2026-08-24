package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB3FF),
    onPrimary = Color(0xFF00325C),
    primaryContainer = Color(0xFF1B4B7F),
    onPrimaryContainer = Color(0xFFD5E4FF),
    secondary = Color(0xFFBAC7DC),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF232B33),
    onSurfaceVariant = Color(0xFFC2C7CF),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A5DA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E4FF),
    onPrimaryContainer = Color(0xFF00193A),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFDFE3EB),
)

@Composable
fun FernbedienungTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
