package com.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.launcher.api.action.DispatchResult
import com.launcher.ui.components.TileCard
import com.launcher.ui.navigation.FlowComponent
import com.launcher.ui.theme.Spacing

/**
 * Renders one flow's slot grid plus a snackbar host that surfaces dispatch
 * failures (US-508). Per ux-quality CHK-020 we chose snackbar over toast:
 * snackbar respects accessibility (announces via TalkBack), supports a
 * Retry action, and does not steal focus.
 *
 * Placeholder slots (slot.action == null) still render but tap is a no-op.
 */
@Composable
fun FlowScreen(
    component: FlowComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.lastDispatchResult) {
        val result = state.lastDispatchResult ?: return@LaunchedEffect
        val failureMessage = when (result) {
            is DispatchResult.Failure              -> "Не удалось выполнить действие. Попробуйте ещё раз."
            is DispatchResult.ProviderUnavailable  -> "Это действие сейчас недоступно. Проверьте, что нужное приложение установлено."
            is DispatchResult.BlockedByPolicy      -> "Действие запрещено настройками."
            is DispatchResult.Ok                   -> null
        }
        if (failureMessage != null) {
            val outcome = snackbarHost.showSnackbar(
                message = failureMessage,
                actionLabel = "Повторить",
                duration = SnackbarDuration.Short,
            )
            if (outcome == SnackbarResult.ActionPerformed) {
                component.retryLastAction()
            }
        }
        component.acknowledgeDispatchResult()
    }

    val pairedDevices by component.pairedDevices.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (state.templateId == "admin_devices") {
                ExtendedFloatingActionButton(
                    onClick = component.onOpenScanner,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Сканировать QR") },
                    modifier = Modifier.testTag("admin_devices_scan_fab"),
                )
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                state.templateId == "admin_devices" ->
                    if (pairedDevices.isNotEmpty()) AdminDevicesList(pairedDevices)
                    else AdminDevicesEmptyState()
                state.slots.isEmpty() -> EmptyFlow(state.flowName)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(state.slots, key = { it.id }) { slot ->
                        TileCard(
                            label = slot.label,
                            onClick = { component.onSlotTap(slot) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminDevicesList(links: List<com.launcher.api.link.Link>) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        links.forEach { link ->
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = "Устройство",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "ID: ${link.managedDeviceId.take(8)}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Подключено",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminDevicesEmptyState() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Нет привязанных устройств",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Spacing.sm))
        Text(
            text = "Нажмите «+» внизу, чтобы отсканировать QR-код другого телефона.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyFlow(flowName: String) {
    Text(
        text = if (flowName.isEmpty()) "Нет слотов." else "$flowName: пока пусто.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.xl),
    )
}
