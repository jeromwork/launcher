package com.launcher.preset.settings

import com.launcher.preset.ecs.get
import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Profile
import com.launcher.preset.model.SettingsMapEntry

/**
 * TASK-69 (FR-000..FR-004) — projects `Profile + Preset.settingsMap` into a
 * [SettingsView]. Shaped for a future shared Home+Settings presentation layer
 * (same builder pattern); Home is NOT wired to it here (rule 4).
 *
 * Row copy stays as raw i18n *keys* — resolution happens in the Compose layer
 * via `StringResolver`/`LocalizedResources` (rule 1: domain never touches
 * platform resource lookup). `rowClassifier` is the derived-editability seam
 * (FR-015) — overridable in tests, defaults to the MVP closed-set [classifyRow].
 */
class SettingsPresentationBuilder(
    private val rowClassifier: (Entity) -> RowClassification = ::classifyRow,
) {

    /**
     * @param settingsMap the active Preset's presentation map (I2 — read from the
     *   Preset, not the Profile).
     */
    fun build(profile: Profile, settingsMap: List<SettingsMapEntry>): SettingsView {
        val sections = settingsMap
            .mapNotNull { entry -> buildRow(profile, entry) }
            .groupBy { (categoryKey, _) -> categoryKey }
            .map { (categoryKey, rows) -> SettingsSection(categoryKey, rows.map { it.second }) }

        val actions: List<AppOperation> = listOf(
            AppOperation.PresetSwitch(current = profile.basedOnPreset),
            AppOperation.PairingQr,
            AppOperation.AdminDevices,
            AppOperation.DataReset,
        )

        return SettingsView(sections = sections, actions = actions)
    }

    /** Returns `categoryKey to SettingRow`, or `null` if `poolRef` resolves to nothing (I5). */
    private fun buildRow(profile: Profile, entry: SettingsMapEntry): Pair<String, SettingRow>? {
        val entity = profile.entities.firstOrNull { it.id == entry.poolRef } ?: return null
        val classification = rowClassifier(entity)
        val row = SettingRow(
            poolRef = entry.poolRef,
            titleKey = classification.titleKey,
            valueText = classification.valueText,
            state = entity.get<LifecycleState>().toRowState(),
            kind = classification.kind,
            editor = classification.editor,
        )
        return entry.categoryKey to row
    }
}

/** Result of classifying one entity's data component for presentation (FR-015). */
data class RowClassification(
    val titleKey: String,
    val valueText: String,
    val kind: RowKind,
    val editor: RowEditor,
)

/**
 * Derived (not stored, FR-015) per-component-type presentation: which i18n
 * title key, how to pre-format the current value (rule 1 — so the Compose
 * layer never does `when(component)`), and whether/how it is editable.
 *
 * "In-app editable" is the closed MVP set (FontSize/Theme/Language/Toolbar) per
 * spec Assumptions; LauncherRole/StatusBarPolicy need a system dialog (FR-011);
 * everything else (AppTile, Sos, structural subtypes) is read-only in Settings
 * at MVP.
 */
fun classifyRow(entity: Entity): RowClassification {
    entity.get<Component.FontSize>()?.let {
        return RowClassification(
            titleKey = "settings.row.font_size.title",
            valueText = "${it.scale}x",
            kind = RowKind.InApp,
            editor = RowEditor.FontScaleStepper,
        )
    }
    entity.get<Component.Theme>()?.let {
        return RowClassification(
            titleKey = "settings.row.theme.title",
            valueText = if (it.darkMode) "settings.row.theme.value.dark" else "settings.row.theme.value.light",
            kind = RowKind.InApp,
            editor = RowEditor.ThemeDarkToggle,
        )
    }
    entity.get<Component.Language>()?.let {
        return RowClassification(
            titleKey = "settings.row.language.title",
            valueText = it.locale,
            kind = RowKind.InApp,
            editor = RowEditor.LocalePicker,
        )
    }
    entity.get<Component.Toolbar>()?.let {
        return RowClassification(
            titleKey = "settings.row.toolbar.title",
            valueText = it.items.joinToString(", "),
            kind = RowKind.InApp,
            editor = RowEditor.ToolbarChips,
        )
    }
    entity.get<Component.LauncherRole>()?.let {
        return RowClassification(
            titleKey = "settings.row.launcher_role.title",
            valueText = "",
            kind = RowKind.SystemDialog,
            editor = RowEditor.None,
        )
    }
    entity.get<Component.StatusBarPolicy>()?.let {
        return RowClassification(
            titleKey = "settings.row.status_bar_policy.title",
            valueText = "",
            kind = RowKind.SystemDialog,
            editor = RowEditor.None,
        )
    }
    entity.get<Component.Sos>()?.let {
        return RowClassification(
            titleKey = "settings.row.sos.title",
            valueText = "settings.row.sos.value",
            kind = RowKind.ReadOnly,
            editor = RowEditor.None,
        )
    }
    entity.get<Component.AppTile>()?.let {
        return RowClassification(
            titleKey = "settings.row.app_tile.title",
            valueText = it.packageName,
            kind = RowKind.ReadOnly,
            editor = RowEditor.None,
        )
    }
    // Structural subtypes (Workspace/Flow/ToolbarButton) are not expected to be
    // referenced from settingsMap, but fail closed to ReadOnly rather than crash.
    return RowClassification(
        titleKey = "settings.row.unknown.title",
        valueText = "",
        kind = RowKind.ReadOnly,
        editor = RowEditor.None,
    )
}

private fun LifecycleState?.toRowState(): RowState = when (this) {
    null -> RowState.Pending
    LifecycleState.Pending -> RowState.Pending
    LifecycleState.Applied -> RowState.Applied
    LifecycleState.Skipped -> RowState.Skipped
    LifecycleState.Unverifiable -> RowState.Unverifiable
    is LifecycleState.Failed -> RowState.Failed
}
