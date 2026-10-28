package com.villegol.appgastos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF146B58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC0F1DF),
    onPrimaryContainer = Color(0xFF06251D),
    secondary = Color(0xFF45627A),
    secondaryContainer = Color(0xFFD5E8FF),
    tertiary = Color(0xFF6B5791),
    background = Color(0xFFF5F8F7),
    onBackground = Color(0xFF151B19),
    surface = Color(0xFFF5F8F7),
    onSurface = Color(0xFF151B19),
    surfaceVariant = Color(0xFFDDE5E1),
    onSurfaceVariant = Color(0xFF46514D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF3F0),
    surfaceContainer = Color(0xFFE8EEEB),
    surfaceContainerHigh = Color(0xFFE2E9E5),
    surfaceContainerHighest = Color(0xFFDBE3DF),
    outline = Color(0xFF75817C),
    outlineVariant = Color(0xFFC5CDC9),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF96E8CB),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF173D34),
    onPrimaryContainer = Color(0xFFC4F4E2),
    secondary = Color(0xFFA9CAEE),
    onSecondary = Color(0xFF123249),
    secondaryContainer = Color(0xFF203B50),
    tertiary = Color(0xFFCAB4F4),
    background = Color(0xFF070C10),
    onBackground = Color(0xFFF0F4F2),
    surface = Color(0xFF070C10),
    onSurface = Color(0xFFF0F4F2),
    surfaceVariant = Color(0xFF3A454C),
    onSurfaceVariant = Color(0xFFB8C3C8),
    surfaceContainerLowest = Color(0xFF04080B),
    surfaceContainerLow = Color(0xFF0D1419),
    surfaceContainer = Color(0xFF121A20),
    surfaceContainerHigh = Color(0xFF182128),
    surfaceContainerHighest = Color(0xFF202B33),
    outline = Color(0xFF849198),
    outlineVariant = Color(0xFF2D3940),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5F1D20)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography().copy(
    displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = Typography().headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
fun AppGastosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
