package com.launcher.ui.senior.util

import com.launcher.api.wizard.AnimationPreferenceProvider

/**
 * Scales an animation duration by the device's reduce-motion preference.
 * 0.0 disables animation entirely (caller should skip animator setup).
 *
 * FR-036a, ACC-2.
 */
fun scaledDurationMillis(
    baseMillis: Int,
    provider: AnimationPreferenceProvider,
): Int {
    val scale = provider.durationScale().coerceAtLeast(0f)
    return (baseMillis * scale).toInt()
}
