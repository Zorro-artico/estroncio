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

private val ObservatoryDarkColorScheme = darkColorScheme(
    primary = BrassGold,
    onPrimary = NightSkyDeep,
    primaryContainer = Color(0xFF3A2D12),
    onPrimaryContainer = Color(0xFFFFE1A3),
    secondary = CelestialCyan,
    onSecondary = NightSkyDeep,
    secondaryContainer = Color(0xFF133447),
    onSecondaryContainer = Color(0xFFBBEAFF),
    tertiary = AuroraEmerald,
    onTertiary = NightSkyDeep,
    background = NightSkyDeep,
    onBackground = StarlightIvory,
    surface = ObservatoryNavy,
    onSurface = StarlightIvory,
    surfaceVariant = AstrolabeSurface,
    onSurfaceVariant = CelestialSlate,
    surfaceContainerLow = ObservatoryNavy,
    surfaceContainer = AstrolabeSurface,
    surfaceContainerHigh = AstrolabeSurfaceHigh,
    outline = MeridianLine,
    outlineVariant = Color(0xFF222B4D),
    error = MarsTerracotta,
    onError = NightSkyDeep
)

private val ParchmentLightColorScheme = lightColorScheme(
    primary = BurnishedBronze,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3DEC0),
    onPrimaryContainer = Color(0xFF3B2400),
    secondary = ObservatoryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0ECF6),
    onSecondaryContainer = Color(0xFF0A303E),
    tertiary = OliveAstrolabe,
    onTertiary = Color.White,
    background = ParchmentPaper,
    onBackground = DeepIndigoInk,
    surface = VellumSurface,
    onSurface = DeepIndigoInk,
    surfaceVariant = VellumCard,
    onSurfaceVariant = IndigoMutedInk,
    surfaceContainerLow = VellumSurface,
    surfaceContainer = VellumCard,
    surfaceContainerHigh = Color(0xFFE0D4BE),
    outline = VellumBorder,
    outlineVariant = Color(0xFFE4DAC6),
    error = CrimsonWax,
    onError = Color.White
)

val AstrolabioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AstrolabioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ObservatoryDarkColorScheme else ParchmentLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AstrolabioTypography,
        shapes = AstrolabioShapes,
        content = content
    )
}
