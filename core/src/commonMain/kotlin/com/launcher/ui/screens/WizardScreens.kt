package com.launcher.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.launcher.api.action.NotApplicableReason
import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderId
import com.launcher.api.action.ProviderState
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
    val providers by component.providers.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }

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
            if (providers.isEmpty()) {
                Text(
                    text = "Нет доступных действий. Проверьте, что устройство подключено к сети и установлены нужные приложения.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("add_slot_empty"),
                )
            }
            providers.forEach { state ->
                ProviderRow(
                    state = state,
                    selected = selected == state.providerId.value,
                    onSelect = { selected = state.providerId.value },
                )
            }
            Spacer(Modifier.size(Spacing.lg))
            Button(
                onClick = component.onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_slot_done"),
                enabled = selected != null,
            ) {
                Text("Готово", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ProviderRow(
    state: ProviderState,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val displayLabel = providerDisplayLabel(state.providerId)
    val testTagBase = "add_slot_provider_${state.providerId.value}"
    when (val availability = state.availability) {
        is ProviderAvailability.Available -> Row(
            modifier = Modifier.fillMaxWidth().testTag(testTagBase),
            selected = selected,
            onClick = onSelect,
            label = displayLabel,
        )
        is ProviderAvailability.Missing -> Row(
            modifier = Modifier.fillMaxWidth().testTag("${testTagBase}_missing"),
            selected = false,
            onClick = onSelect,  // tap → caller will surface install affordance via component.onInstallRequested in a follow-up
            label = "$displayLabel — установить (${availability.installHint?.recommendedPackage ?: "Play Store"})",
        )
        is ProviderAvailability.NotApplicable -> Row(
            modifier = Modifier.fillMaxWidth().testTag("${testTagBase}_na"),
            selected = false,
            onClick = { /* greyed-out: not selectable */ },
            label = "$displayLabel — недоступно (${notApplicableLabel(availability.reason)})",
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun providerDisplayLabel(id: ProviderId): String = when (id) {
    ProviderId.APP             -> "Приложение"
    ProviderId.WHATSAPP        -> "WhatsApp"
    ProviderId.TELEGRAM        -> "Telegram"
    ProviderId.PHONE           -> "Позвонить"
    ProviderId.SMS             -> "Сообщение"
    ProviderId.BROWSER         -> "Браузер"
    ProviderId.YOUTUBE         -> "YouTube"
    ProviderId.SYSTEM_SETTINGS -> "Настройки"
    else                       -> id.value
}

private fun notApplicableLabel(reason: NotApplicableReason): String = when (reason) {
    NotApplicableReason.NoTelephony      -> "нет телефонной связи"
    NotApplicableReason.NoBrowser        -> "нет браузера"
    NotApplicableReason.NoDefaultSmsApp  -> "нет SMS-приложения"
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
    labelColor: Color = Color.Unspecified,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
        )
    }
}
