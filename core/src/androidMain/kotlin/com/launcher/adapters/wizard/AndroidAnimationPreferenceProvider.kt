package com.launcher.adapters.wizard

import android.content.Context
import android.provider.Settings
import com.launcher.api.wizard.AnimationPreferenceProvider

/**
 * Reads `Settings.Global.ANIMATOR_DURATION_SCALE` per FR-036a (reduce-motion).
 * Returns 1.0 if the setting is unreadable (very rare on modern Android).
 */
class AndroidAnimationPreferenceProvider(private val context: Context) : AnimationPreferenceProvider {
    override fun durationScale(): Float {
        return try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f,
            )
        } catch (_: Throwable) {
            1.0f
        }
    }
}
