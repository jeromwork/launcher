package com.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.launcher.ui.navigation.AddFlowWizardComponent
import com.launcher.ui.navigation.AddSlotWizardComponent
import com.launcher.ui.navigation.AdminDevicesComponent
import com.launcher.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFlowWizardScreen(component: AddFlowWizardComponent, modifier: Modifier = Modifier) {
    val templates = listOf("contacts" to "Контакты", "admin_devices" to "Управление телефонами")
    var selected by remember { mutableStateOf(templates.first().first) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Новый flow", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = component.onBack, modifier = Modifier.testTag("add_flow_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                "Выберите шаблон:",
                style = MaterialTheme.typography.titleMedium,
            )
            templates.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_flow_template_$id"),
                    selected = id == selected,
                    onClick = { selected = id },
                    label = label,
                )
            }
            Spacer(Modifier.size(Spacing.lg))
            Button(
                onClick = component.onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_flow_done"),
            ) {
                Text("Добавить", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSlotWizardScreen(component: AddSlotWizardComponent, modifier: Modifier = Modifier) {
    val actionTypes = listOf("call" to "Позвонить", "video" to "Видеозвонок", "open_app" to "Открыть приложение")
    var selected by remember { mutableStateOf(actionTypes.first().first) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Новый слот", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = component.onBack, modifier = Modifier.testTag("add_slot_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                "Что должна делать кнопка:",
                style = MaterialTheme.typography.titleMedium,
            )
            actionTypes.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_slot_type_$id"),
                    selected = id == selected,
                    onClick = { selected = id },
                    label = label,
                )
            }
            Spacer(Modifier.size(Spacing.lg))
            Button(
                onClick = component.onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_slot_done"),
            ) {
                Text("Готово", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDevicesScreen(component: AdminDevicesComponent, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Устройства", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = component.onBack, modifier = Modifier.testTag("admin_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* spec 009 will wire pairing here */ },
                modifier = Modifier.testTag("admin_add_device"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить устройство")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Нет сопряжённых устройств",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Подключите телефон пожилого пользователя через QR-код. Реальная привязка появится в spec 009.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Row(
    modifier: Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
