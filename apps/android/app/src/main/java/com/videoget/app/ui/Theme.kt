package com.videoget.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    secondary = Color(0xFFE11D48),
    onSecondary = Color.White,
    background = Color(0xFFFFF1F2),
    onBackground = Color(0xFF32101A),
    surface = Color.White,
    onSurface = Color(0xFF32101A),
    surfaceVariant = Color(0xFFF8E8EC),
    onSurfaceVariant = Color(0xFF554349),
    outline = Color(0xFF8A747A),
    error = Color(0xFFB91C1C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB9FF),
    onPrimary = Color(0xFF002E69),
    secondary = Color(0xFFFFB1C1),
    onSecondary = Color(0xFF65001E),
    background = Color(0xFF1B1114),
    onBackground = Color(0xFFF8E8EC),
    surface = Color(0xFF271A1E),
    onSurface = Color(0xFFF8E8EC),
    surfaceVariant = Color(0xFF3A292E),
    onSurfaceVariant = Color(0xFFE5C5CD),
    outline = Color(0xFFB99AA2),
    error = Color(0xFFFFB4AB),
)

@Composable
fun VideoGetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
