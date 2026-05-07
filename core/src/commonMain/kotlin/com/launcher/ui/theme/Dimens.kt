package com.launcher.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 8dp grid. See docs/dev/design-system.md §5. */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}

/** Material 3 elevation tokens. See docs/dev/design-system.md §7. */
object Elevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp
}

/** Senior-safe minimum tap target. See docs/dev/design-system.md §6. */
object TapTargets {
    /** Minimum tap target across the whole app. Material 3 default is 48dp. */
    val minimum: Dp = 56.dp

    /** Target for primary launcher tiles (large, easy to hit). */
    val tile: Dp = 96.dp
}

/**
 * Density mode the theme runs in. Affects typography and tap-target scaling.
 *
 * Mapping to presets (per spec 003):
 *   workspace        -> Standard
 *   launcher         -> Comfortable
 *   simple-launcher  -> SeniorSafe
 */
enum class LayoutDensity { Standard, Comfortable, SeniorSafe }

/** Tap-target multiplier per density. */
internal fun Dp.scaleFor(density: LayoutDensity): Dp =
    when (density) {
        LayoutDensity.Standard -> this
        LayoutDensity.Comfortable -> this + 4.dp
        LayoutDensity.SeniorSafe -> this + 8.dp
    }
