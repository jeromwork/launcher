package com.launcher.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.launcher.api.FlowDescriptor
import com.launcher.ui.components.BottomFlowBar
import com.launcher.ui.navigation.HomeComponent
import com.launcher.ui.theme.Spacing
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState

/**
 * Home screen. Renders the current flow's content area on top of [BottomFlowBar].
 * The flow content itself ([FlowScreen]) is not migrated yet (T408) — for now,
 * a placeholder body is shown.
 */
@Composable
fun HomeScreen(
    component: HomeComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            BottomFlowBar(
                flows = state.flows,
                activeFlowId = state.activeFlowId,
                onFlowClick = component::selectFlow,
                onAddFlowClick = component.onAddFlowClick,
                onSettingsClick = component.onSettingsClick,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            FlowContentPlaceholder(
                activeFlow = state.flows.firstOrNull { it.id == state.activeFlowId },
            )
        }
    }
}

@Composable
private fun FlowContentPlaceholder(activeFlow: FlowDescriptor?) {
    Column(
        modifier = Modifier.padding(Spacing.xl),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm),
    ) {
        if (activeFlow == null) {
            Text(
                text = "Загрузка…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = activeFlow.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Слоты появятся здесь после миграции FlowScreen (T408).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("unused")
private fun typeRefForPlatformImports(state: StateFlow<*>?) = state
