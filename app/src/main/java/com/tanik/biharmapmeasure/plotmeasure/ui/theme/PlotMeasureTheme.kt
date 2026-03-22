package com.tanik.biharmapmeasure.plotmeasure.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFFB55836),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF3D6C7),
        onPrimaryContainer = Color(0xFF3D1A0F),
        secondary = Color(0xFF2E6A57),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCE9E2),
        onSecondaryContainer = Color(0xFF0F221C),
        tertiary = Color(0xFF365E7C),
        onTertiary = Color.White,
        background = Color(0xFFFDF8EF),
        onBackground = Color(0xFF1B1F1A),
        surface = Color(0xFFFFFCF7),
        onSurface = Color(0xFF1B1F1A),
        surfaceVariant = Color(0xFFF0E3D3),
        onSurfaceVariant = Color(0xFF4A453F),
        outline = Color(0xFFC6B49E),
        error = Color(0xFFB3261E),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFF1AE88),
        onPrimary = Color(0xFF4D200E),
        primaryContainer = Color(0xFF7C3A1F),
        onPrimaryContainer = Color(0xFFFFDCCD),
        secondary = Color(0xFF9CCCB9),
        onSecondary = Color(0xFF103126),
        secondaryContainer = Color(0xFF204D40),
        onSecondaryContainer = Color(0xFFDCE9E2),
        tertiary = Color(0xFFA7C8E5),
        onTertiary = Color(0xFF163347),
        background = Color(0xFF171410),
        onBackground = Color(0xFFF3EFE5),
        surface = Color(0xFF221D18),
        onSurface = Color(0xFFF3EFE5),
        surfaceVariant = Color(0xFF3B342E),
        onSurfaceVariant = Color(0xFFD5C3B0),
        outline = Color(0xFF9F8C7B),
        error = Color(0xFFFFB4AB),
    )

@Composable
fun PlotMeasureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
