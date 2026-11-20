package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SuperSonicLightColorScheme = lightColorScheme(
    primary = BrandDarkBlue,
    onPrimary = Color.White,
    primaryContainer = BrandSurfaceTint,
    onPrimaryContainer = BrandDeepNavy,
    secondary = BrandLightBlue,
    onSecondary = BrandDeepNavy,
    secondaryContainer = Color(0xFFD9F0FC),
    onSecondaryContainer = BrandDarkBlue,
    tertiary = StatusInfo,
    onTertiary = Color.White,
    tertiaryContainer = StatusInfoContainer,
    onTertiaryContainer = BrandDeepNavy,
    background = BrandSkyBackground,
    onBackground = BrandTextNavy,
    surface = BrandCardWhite,
    onSurface = BrandTextNavy,
    surfaceVariant = BrandSurfaceHigh,
    onSurfaceVariant = BrandTextMuted,
    outline = BrandBorderBlue,
    outlineVariant = Color(0xFFE3F0FA),
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val SuperSonicDarkColorScheme = darkColorScheme(
    primary = BrandLightBlue,
    onPrimary = BrandDeepNavy,
    primaryContainer = BrandDarkBlue,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF93D5F6),
    onSecondary = BrandDeepNavy,
    secondaryContainer = Color(0xFF1A4063),
    onSecondaryContainer = Color(0xFFE5F4FD),
    tertiary = Color(0xFF38BDF8),
    onTertiary = BrandDeepNavy,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    outlineVariant = Color(0xFF1D3A55),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

val SuperSonicShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SuperSonicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SuperSonicDarkColorScheme else SuperSonicLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SuperSonicTypography,
        shapes = SuperSonicShapes,
        content = content
    )
}
