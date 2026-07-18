package com.launcher.preset.settings

import kotlinx.serialization.Serializable

/**
 * TASK-69 — the Settings screen's projection result: `Profile + Preset.settingsMap
 * (+ i18n) -> SettingsView`. Built by [SettingsPresentationBuilder], consumed by
 * `SettingsScreen` (Compose, `app/`) through `SettingsViewModel`.
 *
 * Runtime-only (not persisted, not wire) — `@Serializable`-shaped so a future
 * JSON-driven render (TASK-133) is additive, but no `schemaVersion` field, per
 * data-model.md.
 */
@Serializable
data class SettingsView(
    val sections: List<SettingsSection>,
    val actions: List<AppOperation>,
)

@Serializable
data class SettingsSection(
    val categoryKey: String,
    val rows: List<SettingRow>,
)

@Serializable
data class SettingRow(
    val poolRef: String,
    val titleKey: String,
    val valueText: String,
    val state: RowState,
    val kind: RowKind,
    /**
     * MVP generic-edit affordance hint for [RowKind.InApp] rows — derived by the
     * same domain classifier as [kind] (never a wire field, FR-015). `None` for
     * [RowKind.SystemDialog] / [RowKind.ReadOnly]. A richer JSON-driven editor
     * descriptor is TASK-133's job; this is the minimal seam that lets today's
     * Compose screen render a generic control per row without switching on
     * `Component` subtypes (SC-006).
     */
    val editor: RowEditor = RowEditor.None,
)

/** 1:1 projection of [com.launcher.preset.model.LifecycleState] (FR-004). */
@Serializable
enum class RowState { Applied, Pending, Failed, Skipped, Unverifiable }

/** Derived, NOT stored (FR-015) — whether/how a row can be changed from Settings. */
@Serializable
enum class RowKind { InApp, SystemDialog, ReadOnly }

/** Which generic control the screen renders for an [RowKind.InApp] row. */
@Serializable
enum class RowEditor { None, FontScaleStepper, ThemeDarkToggle, LocalePicker, ToolbarChips }

/** Re-hosted legacy app-operations (FR-020) — NOT Profile components. */
@Serializable
sealed interface AppOperation {
    @Serializable
    data class PresetSwitch(val current: String) : AppOperation

    @Serializable
    data object PairingQr : AppOperation

    @Serializable
    data object AdminDevices : AppOperation

    @Serializable
    data object DataReset : AppOperation
}
