package com.launcher.api.wizard

/** Per FR-036a — reduce-motion support. 0.0 = reduce-motion; 1.0 = normal. */
interface AnimationPreferenceProvider {
    fun durationScale(): Float
}
