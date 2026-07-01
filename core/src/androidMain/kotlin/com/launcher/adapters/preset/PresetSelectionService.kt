package com.launcher.adapters.preset

import com.launcher.api.preset.Preset
import com.launcher.api.preset.PresetRef
import com.launcher.api.preset.ref
import com.launcher.api.profile.ProfileStore
import com.launcher.api.switchstrategy.ProfileSwitchStrategy
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.data.ConfigDocument

/**
 * First-launch selection: load the chosen preset, apply the default
 * [ProfileSwitchStrategy], persist as the active profile. Used by
 * [com.launcher.ui.PresetPickerScreen] callback (US-1, FR-009).
 *
 * Bundled presets are addressed by [slug] (matches the synthesized header
 * id in [com.launcher.api.wizard.data.ConfigParser.parsePreset]).
 */
class PresetSelectionService(
    private val configSource: ConfigSource,
    private val profileStore: ProfileStore,
    private val switchStrategy: ProfileSwitchStrategy,
) {

    suspend fun beginSetup(slug: String): SetupOutcome {
        val result = configSource.load(ConfigKind.Preset, slug)
        val doc = (result as? ConfigSourceResult.Success)?.document as? ConfigDocument.PresetDoc
            ?: return SetupOutcome.PresetNotFound(slug)
        return commit(doc.preset)
    }

    suspend fun commit(preset: Preset): SetupOutcome.Ready {
        val profile = switchStrategy.migrate(from = null, toPreset = preset)
        profileStore.putProfile(preset.ref, profile)
        profileStore.setActive(preset.ref)
        return SetupOutcome.Ready(preset.ref)
    }

    sealed class SetupOutcome {
        data class Ready(val activeRef: PresetRef) : SetupOutcome()
        data class PresetNotFound(val slug: String) : SetupOutcome()
    }
}
