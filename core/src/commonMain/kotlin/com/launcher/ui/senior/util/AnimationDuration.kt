package com.launcher.ui.senior.util

/**
 * Reduce-motion / animation-scale preference source (ACC-2, FR-036a).
 * Carved out of the deleted `com.launcher.api.wizard.AnimationPreferenceProvider`
 * in TASK-126 Phase 7. Kept alongside the only remaining consumer
 * (`scaledDurationMillis`) to avoid a stand-alone one-file port module.
 */
interface AnimationPreferenceProvider {
    /**
     * @return `Global.Settings.ANIMATOR_DURATION_SCALE`-equivalent value.
     * `0f` disables animation entirely; `1f` is baseline; higher slows.
     */
    fun durationScale(): Float
}

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
