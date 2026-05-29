package com.launcher.api.edit

/**
 * Presentation state for tile editing. UI-scoped (screen-scoped) —
 * survives Activity recreation via Decompose state preservation (per
 * state-management.md CHK001).
 *
 * Lifecycle:
 *  - Enter: `EditMode(active = true, target = ..., profile = selector(target.presetId))`.
 *  - Exit: `active = false` (or component disposed).
 *  - Process death: not persisted; on restart user returns to use mode
 *    (mainstream behavior — pending edits already в `ConfigEditor.pendingDraft`).
 *
 * @property active whether the editor is currently in edit mode.
 * @property target who/what is being edited.
 * @property profile derived UX rules. Use [forTarget] to construct atomically.
 */
data class EditMode(
    val active: Boolean,
    val target: TargetIdentity,
    val profile: EditUiProfile,
) {
    companion object {
        /**
         * Construct an inactive [EditMode] for the given target. Profile is
         * derived from `target.presetId` via [EditUiProfileSelector].
         *
         * If profile selection refuses (custom preset, per FR-008b), this
         * factory returns null — caller should display the "Custom presets
         * appear in future updates" placeholder screen.
         */
        fun forTarget(target: TargetIdentity): EditMode? =
            when (val r = EditUiProfileSelector.selectProfile(target.presetId)) {
                is com.launcher.api.result.Outcome.Success ->
                    EditMode(active = false, target = target, profile = r.value)
                is com.launcher.api.result.Outcome.Failure -> null
            }
    }
}
