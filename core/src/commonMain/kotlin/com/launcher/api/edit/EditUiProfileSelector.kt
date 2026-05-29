package com.launcher.api.edit

import com.launcher.api.FlowPreset
import com.launcher.api.result.Outcome

/**
 * Pure-function selector mapping a target preset slug to its [EditUiProfile].
 * Per FR-008 and Q2 clarification — lives in domain (commonMain), no I/O,
 * no Compose dependency. Unit-testable без UI runtime.
 *
 * **Mapping** (FR-008):
 *  - `"workspace"` → [EditUiProfile.AdminProfile].
 *  - `"simple-launcher"` → [EditUiProfile.SeniorProfile].
 *  - other built-in [FlowPreset] slugs (e.g. `"launcher"`) → fallback to
 *    [EditUiProfile.AdminProfile] (least restrictive default, per FR-008b).
 *  - custom user-created preset (slug not in [FlowPreset] enum) →
 *    [Outcome.Failure] с [EditError.ProfileSelectionRequiresCapabilityRegistry]
 *    per FR-008b. F-014 explicitly **refuses** rather than silent-fallback
 *    for custom presets, forcing the decision outside this spec (one-way
 *    door avoidance per CLAUDE.md rule 3).
 *
 * **Exit ramp на F-2** (FR-008a): when Capability Registry Foundation lands,
 * signature changes to `selectProfile(capabilities: Set<Capability>)`. Built-in
 * enum is retained as a pre-packaged composition; this function becomes a
 * thin wrapper.
 */
object EditUiProfileSelector {

    /**
     * Selects the [EditUiProfile] for [presetId].
     *
     * @return [Outcome.Success] with the profile, or
     *   [Outcome.Failure] with [EditError.ProfileSelectionRequiresCapabilityRegistry]
     *   when [presetId] is not a built-in [FlowPreset] slug.
     */
    fun selectProfile(presetId: String): Outcome<EditUiProfile, EditError> {
        val preset = FlowPreset.fromSlug(presetId)
            ?: return Outcome.Failure(EditError.ProfileSelectionRequiresCapabilityRegistry)
        val profile = when (preset) {
            FlowPreset.WORKSPACE -> EditUiProfile.AdminProfile
            FlowPreset.SIMPLE_LAUNCHER -> EditUiProfile.SeniorProfile
            FlowPreset.LAUNCHER -> EditUiProfile.AdminProfile // FR-008b — built-in unknown silent fallback
        }
        return Outcome.Success(profile)
    }
}
