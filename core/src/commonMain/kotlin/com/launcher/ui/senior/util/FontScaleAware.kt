package com.launcher.ui.senior.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/**
 * Returns the active font scale (system × user override).
 *
 * Caller can clamp / cap to a maximum sensible value. We do NOT cap here:
 * elderly users may legitimately use 2.0× or higher; primitives are wrap-
 * content and tolerate it.
 *
 * FR-036.
 */
@Composable
fun rememberFontScaleAware(userOverride: Float? = null): Float {
    val systemScale = LocalDensity.current.fontScale
    return userOverride ?: systemScale
}
