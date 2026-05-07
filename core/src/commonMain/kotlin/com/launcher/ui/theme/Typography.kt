package com.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3 baseline + senior-safe overrides. See docs/dev/design-system.md §3.

private val baseline = Typography()

internal val LauncherTypography = Typography(
    // display* / headline* unchanged from Material 3 baseline.
    displayLarge = baseline.displayLarge,
    displayMedium = baseline.displayMedium,
    displaySmall = baseline.displaySmall,
    headlineLarge = baseline.headlineLarge,
    headlineMedium = baseline.headlineMedium,
    headlineSmall = baseline.headlineSmall,
    titleLarge = baseline.titleLarge,

    // titleMedium 16sp -> 18sp
    titleMedium = baseline.titleMedium.copy(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium,
    ),
    // titleSmall 14sp -> 16sp
    titleSmall = baseline.titleSmall.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),

    // bodyLarge 16sp -> 18sp (senior-safe minimum body size)
    bodyLarge = baseline.bodyLarge.copy(
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    // bodyMedium 14sp -> 16sp
    bodyMedium = baseline.bodyMedium.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    // bodySmall 12sp -> 14sp
    bodySmall = baseline.bodySmall.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),

    // labelLarge 14sp -> 16sp (button label)
    labelLarge = baseline.labelLarge.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    // labelMedium 12sp -> 14sp
    labelMedium = baseline.labelMedium.copy(
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    // labelSmall 11sp -> 12sp
    labelSmall = baseline.labelSmall.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

/**
 * Multiplier applied to typography per [LayoutDensity].
 * Used by [LauncherTheme] to scale all roles for the simple-launcher / launcher presets.
 */
internal fun TextStyle.scaleFor(density: LayoutDensity): TextStyle =
    when (density) {
        LayoutDensity.Standard -> this
        LayoutDensity.Comfortable -> copy(
            fontSize = fontSize * 1.10f,
            lineHeight = lineHeight * 1.10f,
        )
        LayoutDensity.SeniorSafe -> copy(
            fontSize = fontSize * 1.20f,
            lineHeight = lineHeight * 1.20f,
        )
    }

internal fun Typography.scaleFor(density: LayoutDensity): Typography =
    Typography(
        displayLarge = displayLarge.scaleFor(density),
        displayMedium = displayMedium.scaleFor(density),
        displaySmall = displaySmall.scaleFor(density),
        headlineLarge = headlineLarge.scaleFor(density),
        headlineMedium = headlineMedium.scaleFor(density),
        headlineSmall = headlineSmall.scaleFor(density),
        titleLarge = titleLarge.scaleFor(density),
        titleMedium = titleMedium.scaleFor(density),
        titleSmall = titleSmall.scaleFor(density),
        bodyLarge = bodyLarge.scaleFor(density),
        bodyMedium = bodyMedium.scaleFor(density),
        bodySmall = bodySmall.scaleFor(density),
        labelLarge = labelLarge.scaleFor(density),
        labelMedium = labelMedium.scaleFor(density),
        labelSmall = labelSmall.scaleFor(density),
    )
