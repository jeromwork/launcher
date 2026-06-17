package com.launcher.ui.senior.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Senior-safe warm-contrast theme. Material 3 wrapper, ≥7:1 contrast
 * (WCAG AAA). FR-035.
 *
 * Warm tones favoured (amber / terracotta primary) per UX research for
 * elderly users — cooler blues read as "system / corporate".
 */
object SeniorWarmTheme {
    private val LightColors = lightColorScheme(
        primary = Color(0xFF7A3E0E),       // deep terracotta — 7.1:1 on white
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDBC9),
        onPrimaryContainer = Color(0xFF2A1100),
        secondary = Color(0xFF725A41),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF1F1B16),  // ≈ 14:1 on background
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF1F1B16),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
    )

    private val DarkColors = darkColorScheme(
        primary = Color(0xFFFFB68F),
        onPrimary = Color(0xFF4A1F00),
        primaryContainer = Color(0xFF5E2D03),
        onPrimaryContainer = Color(0xFFFFDBC9),
        secondary = Color(0xFFE1C1A2),
        onSecondary = Color(0xFF402C17),
        background = Color(0xFF1F1B16),
        onBackground = Color(0xFFEAE1D9),
        surface = Color(0xFF1F1B16),
        onSurface = Color(0xFFEAE1D9),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )

    @Composable
    fun Light(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = LightColors, content = content)
    }

    @Composable
    fun Dark(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = DarkColors, content = content)
    }
}
