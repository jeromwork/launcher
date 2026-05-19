package com.launcher.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.launcher.ui.gate.sevenTapAdminGate
import com.launcher.ui.navigation.HomeComponent
import com.launcher.ui.theme.Spacing

/**
 * Home screen. Renders the active flow's [FlowScreen] in its content area, on top
 * of [BottomFlowBar]. Selecting a tab in the bottom bar activates a new
 * [com.launcher.ui.navigation.FlowComponent] in the home's child slot.
 *
 * [topSlot] (spec 006 FR-026/027): platform-specific banner host injected from
 * `:app` (HomeBannerHost). Renders above the flow content. Default empty slot
 * keeps commonMain testable без Android dependency.
 */
@Composable
fun HomeScreen(
    component: HomeComponent,
    modifier: Modifier = Modifier,
    topSlot: @Composable () -> Unit = {},
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Spec 010 T100 — 7-tap admin gate. Tile clicks inside
                // FlowScreen consume the touch via their own pointerInput, so
                // the gate only fires on whitespace taps (FR-021 non-
                // interactive constraint).
                .sevenTapAdminGate(onTriggered = component.onSevenTapTriggered),
        ) {
            // Spec 006 banner stack. Empty slot when no banners visible — нулевая высота.
            topSlot()
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
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
}
