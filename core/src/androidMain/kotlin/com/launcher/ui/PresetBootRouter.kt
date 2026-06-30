package com.launcher.ui

import com.launcher.adapters.preset.PresetReminderService
import com.launcher.api.preset.Preset
import com.launcher.api.preset.PresetRef
import com.launcher.api.profile.ProfileData
import com.launcher.api.profile.ProfileStore
import com.launcher.api.profile.SettingEntry
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.data.ConfigDocument

/**
 * Decides the boot-time UI path (FR-029, FR-030, FR-031, US-3, US-7).
 *
 * Inputs: persisted [ProfileStore] state + bundled presets via [ConfigSource].
 * Output: [BootDecision] for the host Activity to execute.
 *
 * Boot-time settings check (R4 + Clarification #10): when an active preset
 * is loaded, immediately classifies critical missing settings so the host
 * can render [HomeBanner] together with HomeActivity.
 */
class PresetBootRouter(
    private val profileStore: ProfileStore,
    private val configSource: ConfigSource,
    private val reminderService: PresetReminderService,
) {

    suspend fun decide(): BootDecision {
        val active = profileStore.getActive()
        if (active == null) {
            // First launch — picker is needed. Legacy migration is handled
            // inside PreferencesProfileStore (FR-015); if it fired, active
            // would be non-null already.
            return BootDecision.ShowPicker
        }
        val (ref, profile) = active
        val preset = loadPreset(ref) ?: return BootDecision.ShowPicker
        val criticalMissing = reminderService.computeCriticalMissing(profile)
        return BootDecision.ShowHome(ref, preset, profile, criticalMissing)
    }

    private suspend fun loadPreset(ref: PresetRef): Preset? {
        // Lookup by slug derived from uid (bundled presets use slug as id).
        // For non-bundled (future ImportConfigSource) the ref-uid lookup
        // would replace this — see TODO(shareability).
        val slug = ref.uid.substringAfterLast('.')
        val result = configSource.load(ConfigKind.Preset, slug)
        val doc = (result as? ConfigSourceResult.Success)?.document as? ConfigDocument.PresetDoc
        return doc?.preset
    }

    sealed class BootDecision {
        data object ShowPicker : BootDecision()
        data class ShowHome(
            val ref: PresetRef,
            val preset: Preset,
            val profile: ProfileData,
            val criticalMissing: List<SettingEntry>,
        ) : BootDecision()
    }
}
