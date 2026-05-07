package com.launcher.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.launcher.ui.components.BottomFlowBar
import com.launcher.ui.navigation.HomeComponent
import com.launcher.ui.theme.Spacing

/**
 * Home screen. Renders the active flow's [FlowScreen] in its content area, on top
 * of [BottomFlowBar]. Selecting a tab in the bottom bar activates a new
 * [com.launcher.ui.navigation.FlowComponent] in the home's child slot.
 */
@Composable
fun HomeScreen(
    component: HomeComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    val flowSlot by component.flowSlot.subscribeAsState()
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val active = flowSlot.child?.instance
            if (active != null) {
                FlowScreen(component = active)
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Загрузка…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
