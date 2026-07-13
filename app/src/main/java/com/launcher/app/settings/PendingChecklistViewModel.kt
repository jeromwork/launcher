package com.launcher.app.settings

import com.launcher.api.localization.StringResolver
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.ProfileComponent
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.port.PresetSource
import com.launcher.preset.port.ProfileStore

/**
 * TASK-126 T070 — Settings pending-setup checklist driven by the new preset
 * runtime. Replaces the legacy WizardManifest + WizardEngine lookup with a
 * straight walk over [ProfileStore] + [PresetSource.settingsMap]:
 *
 * - The pending list = every `ProfileComponent` whose `status` is not
 *   [ComponentStatus.Applied] (Pending / Failed / Skipped can all still be
 *   surfaced to the user in Settings) AND whose `wizardBehavior` is
 *   Interactive (auto-applied / initial-default components never need
 *   user attention).
 * - Label lookup uses the [SettingsMapEntry.categoryKey] of the matching
 *   entry in the active preset. If the component has no settingsMap entry
 *   the raw `id` is used as fallback (matches the previous behaviour).
 * - `isRequired` mirrors [ProfileComponent.critical] one-to-one — the wire
 *   `Preset.wizardFlow[i].behavior != null` distinction is dropped: the
 *   ReconcileEngine is the source of truth for whether a step blocks.
 */
class PendingChecklistViewModel(
    private val profileStore: ProfileStore,
    private val presetSource: PresetSource,
    @Suppress("unused") private val stringResolver: StringResolver,
) {
    suspend fun load(): PendingChecklistState {
        val profile = profileStore.load() ?: return PendingChecklistState(emptyList())
        val preset = presetSource.loadPreset(profile.basedOnPreset)
        val labelByRef: Map<String, String> = preset
            ?.settingsMap
            ?.associate { it.poolRef to it.categoryKey }
            .orEmpty()

        val items = profile.components
            .filter { it.needsAttention() }
            .map { pc ->
                PendingChecklistState.Item(
                    refId = pc.id,
                    labelKey = labelByRef[pc.id] ?: pc.id,
                    isRequired = pc.critical,
                    status = pc.status,
                )
            }
        return PendingChecklistState(items = items)
    }

    private fun ProfileComponent.needsAttention(): Boolean =
        wizardBehavior == WizardBehavior.Interactive && status != ComponentStatus.Applied
}

data class PendingChecklistState(val items: List<Item>) {
    data class Item(
        val refId: String,
        val labelKey: String,
        val isRequired: Boolean,
        val status: ComponentStatus,
    )
}
