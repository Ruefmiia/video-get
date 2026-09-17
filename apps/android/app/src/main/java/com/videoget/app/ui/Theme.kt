package com.videoget.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF171717),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF171717),
    secondary = Color(0xFF404040),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E5E5),
    onSecondaryContainer = Color(0xFF171717),
    tertiary = Color(0xFF525252),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF404040),
    outline = Color(0xFF737373),
    outlineVariant = Color(0xFFD4D4D4),
    error = Color(0xFF262626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFE5E5E5),
    onErrorContainer = Color(0xFF171717),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFF404040),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFD4D4D4),
    onSecondary = Color(0xFF171717),
    secondaryContainer = Color(0xFF404040),
    onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFFA3A3A3),
    onTertiary = Color(0xFF171717),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFD4D4D4),
    outline = Color(0xFFA3A3A3),
    outlineVariant = Color(0xFF404040),
    error = Color(0xFFF5F5F5),
    onError = Color(0xFF171717),
    errorContainer = Color(0xFF404040),
    onErrorContainer = Color(0xFFF5F5F5),
)

private val VideoGetTypography = Typography(
    titleLarge = Typography().titleLarge.copy(
        fontWeight = FontWeight.Black,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = Typography().bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = Typography().bodyMedium.copy(lineHeight = 21.sp),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val VideoGetShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun VideoGetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = VideoGetTypography,
        shapes = VideoGetShapes,
        content = content,
    )
}
