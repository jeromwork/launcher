package com.launcher.api.switchstrategy

import com.launcher.api.preset.Preset
import com.launcher.api.profile.ProfileData

/**
 * Strategy port for deriving a new [ProfileData] when activating a preset.
 *
 * Default = [CopyOnActivateStrategy] — copies preset's `abstractProfile`,
 * ignores `from`. Future strategies (MergeStrategy, BindingsCarryOverStrategy)
 * are additive — preset wire format does not change.
 */
interface ProfileSwitchStrategy {
    fun migrate(from: ProfileData?, toPreset: Preset): ProfileData
}
