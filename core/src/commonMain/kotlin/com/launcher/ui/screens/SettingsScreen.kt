package com.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.launcher.api.FlowPreset
import com.launcher.ui.navigation.SettingsComponent
import com.launcher.ui.theme.Spacing

private fun FlowPreset.label(): String = when (this) {
    FlowPreset.WORKSPACE -> "Workspace"
    FlowPreset.LAUNCHER -> "Launcher"
    FlowPreset.SIMPLE_LAUNCHER -> "Simple launcher"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    component: SettingsComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = component.onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text("Язык", style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text("Русский", style = MaterialTheme.typography.bodyLarge) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                headlineContent = { Text("Пресет", style = MaterialTheme.typography.titleMedium) },
                supportingContent = {
                    Text(
                        state.activePreset.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("settings_preset_value"),
                    )
                },
                trailingContent = {
                    OutlinedButton(
                        onClick = component::openPresetPicker,
                        modifier = Modifier.testTag("settings_change_preset"),
                    ) {
                        Text("Сменить", style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                headlineContent = { Text("Удалённое управление", style = MaterialTheme.typography.titleMedium) },
                supportingContent = {
                    Text(
                        if (state.remoteControlEnabled) "Включено" else "Выключено",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.remoteControlEnabled,
                        onCheckedChange = { component.toggleRemoteControl() },
                        modifier = Modifier.testTag("settings_remote_toggle"),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
            if (state.remoteControlEnabled) {
                ListItem(
                    headlineContent = { Text("Показать QR-код", style = MaterialTheme.typography.titleMedium) },
                    trailingContent = {
                        OutlinedButton(
                            onClick = component::openQrPlaceholder,
                            modifier = Modifier.testTag("settings_show_qr"),
                        ) {
                            Text("Показать", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.size(Spacing.lg))
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = component::confirmReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_reset_data"),
                ) {
                    Text("Сбросить данные", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.size(Spacing.xl))
        }
    }

    if (state.presetPickerVisible) {
        AlertDialog(
            onDismissRequest = component::closePresetPicker,
            modifier = Modifier.testTag("preset_picker_dialog"),
            title = { Text("Выберите пресет", style = MaterialTheme.typography.headlineSmall) },
            text = {
                LazyColumn {
                    items(FlowPreset.entries.toList()) { preset ->
                        TextButton(
                            onClick = { component.selectPreset(preset) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preset_picker_${preset.slug}"),
                        ) {
                            Text(preset.label(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = component::closePresetPicker) {
                    Text("Закрыть", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }

    if (state.qrPlaceholderVisible) {
        AlertDialog(
            onDismissRequest = component::closeQrPlaceholder,
            title = { Text("QR-код", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "Реальный QR будет здесь после реализации pairing (spec 007).",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = component::closeQrPlaceholder) {
                    Text("Понятно", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }

    if (state.resetConfirmVisible) {
        AlertDialog(
            onDismissRequest = component::cancelReset,
            modifier = Modifier.testTag("reset_confirm_dialog"),
            title = { Text("Сбросить данные?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "Активный пресет и связанные настройки будут удалены. Запустится мастер первого запуска.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = component::executeReset,
                    modifier = Modifier.testTag("reset_confirm_yes"),
                ) {
                    Text("Сбросить", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = component::cancelReset) {
                    Text("Отмена", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}
