package com.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Local provider so screens can read the active density without dragging it
 * through every Composable signature.
 */
val LocalLayoutDensity = compositionLocalOf { LayoutDensity.Standard }

/**
 * Root theme wrapper. Apply once at the top of each Activity / `setContent`.
 *
 * @param preset which preset is active; selects [LayoutDensity] (see Dimens.kt).
 * @param darkTheme `true` to force dark mode; default follows system.
 */
@Composable
fun LauncherTheme(
    preset: String? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val density = densityFor(preset)
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val typography = LauncherTypography.scaleFor(density)
    val shapes = LauncherShapes

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDensity provides density) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

internal fun densityFor(preset: String?): LayoutDensity =
    when (preset) {
        "workspace" -> LayoutDensity.Standard
        "launcher" -> LayoutDensity.Comfortable
        "simple-launcher" -> LayoutDensity.SeniorSafe
        else -> LayoutDensity.Standard
    }

@Composable
internal expect fun isSystemInDarkTheme(): Boolean
