package com.launcher.api.edit

/**
 * UX rules selector. Determines presentation-layer affordances for a given
 * target [EditMode] — picker tab visibility (FR-019), conflict resolution
 * pattern (FR-016 / Q7), use-mode rendering rules (FR-021).
 *
 * Per Q4 clarification 2026-05-29: **edit mode UX is universal** across both
 * profiles — mainstream Android conventions (long-press entry, drag-and-drop,
 * snackbar undo, jiggle, unified picker). Profile differences live ONLY in:
 *  - picker tab filtering ([com.launcher.api.edit.PickerType] visibility),
 *  - concurrent edit conflict UX ([com.launcher.api.edit.EditError] handling),
 *  - use-mode home rendering (governed by existing спека 003 / 010 Simple
 *    Launcher rendering rules — not by this type).
 *
 * Selected via [EditUiProfileSelector.selectProfile] from [TargetIdentity.presetId].
 * Per FR-008a — placeholder until F-2 Capability Registry Foundation lands;
 * when F-2 ships, signature changes to `selectProfile(capabilities: Set<Capability>)`.
 *
 * Pure-Kotlin (no platform deps). Konsist gate T170 enforces.
 */
sealed class EditUiProfile {
    /** Workspace target (admin's own home OR Workspace on senior's device). */
    data object AdminProfile : EditUiProfile()

    /** Simple Launcher target (бабушкин home OR future TV-class targets). */
    data object SeniorProfile : EditUiProfile()
}
