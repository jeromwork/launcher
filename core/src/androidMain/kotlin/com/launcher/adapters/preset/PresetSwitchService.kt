package com.launcher.adapters.preset

import com.launcher.api.preset.Preset
import com.launcher.api.preset.PresetRef
import com.launcher.api.preset.ref
import com.launcher.api.profile.ProfileData
import com.launcher.api.profile.ProfileStore
import com.launcher.api.switchstrategy.ProfileSwitchStrategy
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.data.ConfigDocument

/**
 * Switching from one active preset to another (US-2, FR-014). If a profile
 * already exists for the target preset — restore it. Otherwise apply the
 * default [ProfileSwitchStrategy] (CopyOnActivate). The mini-wizard for
 * critical missing is launched by the caller using
 * [PresetReminderService.computeCriticalMissing] on the resulting profile.
 */
class PresetSwitchService(
    private val configSource: ConfigSource,
    private val profileStore: ProfileStore,
    private val switchStrategy: ProfileSwitchStrategy,
) {

    suspend fun switchTo(slug: String): SwitchOutcome {
        val result = configSource.load(ConfigKind.Preset, slug)
        val doc = (result as? ConfigSourceResult.Success)?.document as? ConfigDocument.PresetDoc
            ?: return SwitchOutcome.PresetNotFound(slug)
        val newPreset = doc.preset
        val state = profileStore.load()
        val existing = state.profiles[newPreset.ref.toCompositeKey()]
        val nextProfile = existing ?: switchStrategy.migrate(
            from = state.activePresetRef?.let { state.profiles[it.toCompositeKey()] },
            toPreset = newPreset,
        )
        profileStore.putProfile(newPreset.ref, nextProfile)
        profileStore.setActive(newPreset.ref)
        return SwitchOutcome.Switched(newPreset, nextProfile, restored = existing != null)
    }

    sealed class SwitchOutcome {
        data class Switched(
            val preset: Preset,
            val profile: ProfileData,
            val restored: Boolean,
        ) : SwitchOutcome()
        data class PresetNotFound(val slug: String) : SwitchOutcome()
    }
}
