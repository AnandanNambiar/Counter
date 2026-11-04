package com.zenguard.counter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LightingColorFilter


private val LightColors = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF3A1A00),
    secondary = Color(0xFF5D4037),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F0),
    onBackground = Color(0xFF2B1B10),
    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF2B1B10),
    surfaceContainerHighest = Color(0xFFF5E3D3)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB77C),
    onPrimary = Color(0xFF4E2600),
    primaryContainer = Color(0xFF7A3900),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFD7B8A8),
    onSecondary = Color(0xFF3B2A20),
    background = Color(0xFF1F1410),
    onBackground = Color(0xFFF0DFD3),
    surface = Color(0xFF1F1410),
    onSurface = Color(0xFFF0DFD3),
    surfaceContainerHighest = Color(0xFF3A2C24)
)

@Composable
fun CounterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
