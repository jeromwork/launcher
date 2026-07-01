package com.launcher.api.switchstrategy

import com.launcher.api.preset.Preset
import com.launcher.api.profile.AppliedState
import com.launcher.api.profile.Layout
import com.launcher.api.profile.ProfileData
import com.launcher.api.profile.SettingEntry

/**
 * Default strategy: ignore prior [ProfileData], copy from preset's
 * [com.launcher.api.preset.AbstractProfile] if present, settings start
 * [AppliedState.NotApplied].
 */
class CopyOnActivateStrategy : ProfileSwitchStrategy {
    override fun migrate(from: ProfileData?, toPreset: Preset): ProfileData {
        val abstract = toPreset.abstractProfile
        return ProfileData(
            layout = abstract?.layout ?: Layout.empty(),
            bindings = abstract?.bindings ?: emptyList(),
            settings = toPreset.configs.map { SettingEntry(it, AppliedState.NotApplied) },
            unassigned = emptyList(),
        )
    }
}
