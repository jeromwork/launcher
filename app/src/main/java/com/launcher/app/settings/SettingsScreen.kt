package com.launcher.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.launcher.api.localization.StringResolver
import com.launcher.preset.port.LocalizedResources
import com.launcher.preset.settings.AppOperation
import com.launcher.preset.settings.RowEditor
import com.launcher.preset.settings.RowKind
import com.launcher.preset.settings.RowState
import com.launcher.preset.settings.SettingRow
import com.launcher.preset.settings.SettingsSection
import com.launcher.ui.theme.Spacing
import com.launcher.ui.theme.TapTargets
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * TASK-69 — Settings screen as a **projection of the Profile** (Wizard is the
 * first projection). Dumb render of [SettingsUiState.view]: no `when(component)`
 * subtype matching, no Android API calls (SC-006, FR-008) — every row already
 * carries its title/value/state/kind/editor from the domain builder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    i18n: LocalizedResources,
    stringResolver: StringResolver,
    onChange: (poolRef: String, params: JsonObject) -> Unit,
    onBack: () -> Unit,
    onPresetChange: (current: String) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenAdminDevices: () -> Unit,
    onResetConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    /** FR-017 — locale-divergence banner + pending-setup checklist, preserved from the ECS-era host. */
    headerContent: @Composable (() -> Unit)? = null,
    /** FR-017 — "walk through all settings" re-run entry, preserved from the ECS-era host. */
    footerContent: @Composable (() -> Unit)? = null,
) {
    var resetConfirmVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResolver.resolve("settings_title"), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResolver.resolve("settings_back_description"))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (headerContent != null) {
                item { headerContent() }
            }
            items(uiState.view.sections) { section ->
                SectionHeader(section = section, i18n = i18n)
                section.rows.forEach { row ->
                    SettingRowItem(row = row, i18n = i18n, stringResolver = stringResolver, onChange = onChange)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (uiState.view.actions.isNotEmpty()) {
                item {
                    Spacer(Modifier.size(Spacing.lg))
                    Text(
                        text = stringResolver.resolve("settings_actions_header"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
            }
            items(uiState.view.actions) { action ->
                AppOperationRow(
                    action = action,
                    stringResolver = stringResolver,
                    onPresetChange = onPresetChange,
                    onOpenPairing = onOpenPairing,
                    onOpenAdminDevices = onOpenAdminDevices,
                    onResetClick = { resetConfirmVisible = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (footerContent != null) {
                item { footerContent() }
            }
            item { Spacer(Modifier.size(Spacing.xl)) }
        }
    }

    if (resetConfirmVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmVisible = false },
            modifier = Modifier.testTag("reset_confirm_dialog"),
            title = { Text(stringResolver.resolve("settings_reset_confirm_title"), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(stringResolver.resolve("settings_reset_confirm_body"), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(
                    onClick = { resetConfirmVisible = false; onResetConfirmed() },
                    modifier = Modifier.testTag("reset_confirm_yes"),
                ) { Text(stringResolver.resolve("settings_reset_confirm_yes")) }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmVisible = false }) {
                    Text(stringResolver.resolve("settings_reset_confirm_cancel"))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(section: SettingsSection, i18n: LocalizedResources) {
    Text(
        text = i18n.resolve(section.categoryKey),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

@Composable
private fun SettingRowItem(
    row: SettingRow,
    i18n: LocalizedResources,
    stringResolver: StringResolver,
    onChange: (String, JsonObject) -> Unit,
) {
    // SC-011: Failed/Skipped rows show status only — never a tappable-looking control.
    val brokenState = row.state == RowState.Failed || row.state == RowState.Skipped
    val title = i18n.resolve(row.titleKey)
    val value = i18n.resolve(row.valueText)
    val stateLabel = stateLabel(row.state, stringResolver)

    ListItem(
        modifier = Modifier.semantics { contentDescription = "$title, $value, $stateLabel" },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                if (value.isBlank()) stateLabel else "$value — $stateLabel",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        trailingContent = if (brokenState) null else {
            {
                when (row.kind) {
                    RowKind.SystemDialog -> ChangeButton(stringResolver) { onChange(row.poolRef, JsonObject(emptyMap())) }
                    RowKind.InApp -> InAppEditor(row = row, value = value, stringResolver = stringResolver, onChange = onChange)
                    RowKind.ReadOnly -> Unit
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun ChangeButton(stringResolver: StringResolver, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(width = TapTargets.minimum + 32.dp, height = TapTargets.minimum),
    ) {
        Text(stringResolver.resolve("settings_row_change"), style = MaterialTheme.typography.labelLarge)
    }
}

/** Generic edit control per [RowEditor] — dispatch on the domain-derived enum, never on `Component`. */
@Composable
private fun InAppEditor(
    row: SettingRow,
    value: String,
    stringResolver: StringResolver,
    onChange: (String, JsonObject) -> Unit,
) {
    when (row.editor) {
        RowEditor.FontScaleStepper -> FontScaleStepper(row.poolRef, onChange)
        RowEditor.ThemeDarkToggle -> ThemeDarkToggle(row.poolRef, isDark = row.valueText == "settings.row.theme.value.dark", onChange)
        RowEditor.LocalePicker -> LocalePicker(row.poolRef, stringResolver, onChange)
        RowEditor.ToolbarChips -> ToolbarChips(row.poolRef, value, onChange)
        RowEditor.None -> Unit
    }
}

private val FONT_SCALE_STEPS = listOf(1.0f, 1.3f, 1.6f, 2.0f)

@Composable
private fun FontScaleStepper(poolRef: String, onChange: (String, JsonObject) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            modifier = Modifier.size(TapTargets.minimum),
            onClick = {
                onChange(poolRef, JsonObject(mapOf("scale" to JsonPrimitive(FONT_SCALE_STEPS.first()))))
            },
        ) { Text("A–", style = MaterialTheme.typography.titleMedium) }
        IconButton(
            modifier = Modifier.size(TapTargets.minimum),
            onClick = {
                onChange(poolRef, JsonObject(mapOf("scale" to JsonPrimitive(FONT_SCALE_STEPS.last()))))
            },
        ) { Text("A+", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun ThemeDarkToggle(poolRef: String, isDark: Boolean, onChange: (String, JsonObject) -> Unit) {
    Box(modifier = Modifier.size(TapTargets.minimum), contentAlignment = Alignment.Center) {
        Switch(
            checked = isDark,
            onCheckedChange = { checked ->
                onChange(poolRef, JsonObject(mapOf("darkMode" to JsonPrimitive(checked))))
            },
        )
    }
}

private val LOCALE_OPTIONS = listOf("system", "ru", "en")

@Composable
private fun LocalePicker(poolRef: String, stringResolver: StringResolver, onChange: (String, JsonObject) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.size(width = TapTargets.minimum + 24.dp, height = TapTargets.minimum),
        ) { Text(stringResolver.resolve("settings_row_change"), style = MaterialTheme.typography.labelLarge) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LOCALE_OPTIONS.forEach { locale ->
                DropdownMenuItem(
                    text = { Text(locale) },
                    onClick = {
                        expanded = false
                        onChange(poolRef, JsonObject(mapOf("locale" to JsonPrimitive(locale))))
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolbarChips(poolRef: String, currentValue: String, onChange: (String, JsonObject) -> Unit) {
    val items = remember(currentValue) { currentValue.split(", ").filter { it.isNotBlank() } }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items.forEach { itemId ->
            TextButton(
                modifier = Modifier.widthIn(min = TapTargets.minimum),
                onClick = {
                    val remaining = buildJsonArray { (items - itemId).forEach { add(JsonPrimitive(it)) } }
                    onChange(poolRef, JsonObject(mapOf("items" to remaining)))
                },
            ) { Text("$itemId ×") }
        }
    }
}

@Composable
private fun AppOperationRow(
    action: AppOperation,
    stringResolver: StringResolver,
    onPresetChange: (String) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenAdminDevices: () -> Unit,
    onResetClick: () -> Unit,
) {
    when (action) {
        is AppOperation.PresetSwitch -> ListItem(
            headlineContent = { Text(stringResolver.resolve("settings_action_preset"), style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(action.current, modifier = Modifier.testTag("settings_preset_value")) },
            trailingContent = {
                OutlinedButton(
                    onClick = { onPresetChange(action.current) },
                    modifier = Modifier.testTag("settings_change_preset"),
                ) { Text(stringResolver.resolve("settings_action_change")) }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
        AppOperation.PairingQr -> ListItem(
            headlineContent = { Text(stringResolver.resolve("settings_action_pairing"), style = MaterialTheme.typography.titleMedium) },
            trailingContent = {
                OutlinedButton(onClick = onOpenPairing, modifier = Modifier.testTag("settings_open_pairing")) {
                    Text(stringResolver.resolve("settings_action_open"))
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
        AppOperation.AdminDevices -> ListItem(
            headlineContent = { Text(stringResolver.resolve("settings_action_admin_devices"), style = MaterialTheme.typography.titleMedium) },
            trailingContent = {
                OutlinedButton(onClick = onOpenAdminDevices, modifier = Modifier.testTag("settings_admin_devices")) {
                    Text(stringResolver.resolve("settings_action_open"))
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
        AppOperation.DataReset -> Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = onResetClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = TapTargets.minimum).testTag("settings_reset_data"),
            ) { Text(stringResolver.resolve("settings_action_reset")) }
        }
    }
}

@Composable
private fun stateLabel(state: RowState, stringResolver: StringResolver): String = when (state) {
    RowState.Applied -> stringResolver.resolve("settings_state_applied")
    RowState.Pending -> stringResolver.resolve("settings_state_pending")
    RowState.Failed -> stringResolver.resolve("settings_state_failed")
    RowState.Skipped -> stringResolver.resolve("settings_state_skipped")
    RowState.Unverifiable -> stringResolver.resolve("settings_state_unverifiable")
}
