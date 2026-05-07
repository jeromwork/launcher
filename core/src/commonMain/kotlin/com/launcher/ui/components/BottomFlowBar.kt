package com.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.launcher.api.FlowDescriptor
import com.launcher.ui.theme.Spacing
import com.launcher.ui.theme.TapTargets

/**
 * Bottom navigation bar listing flows + an "add flow" + "settings" affordance.
 * Tabs are scrollable; the active tab is rendered with a filled tonal style.
 */
@Composable
fun BottomFlowBar(
    flows: List<FlowDescriptor>,
    activeFlowId: String?,
    onFlowClick: (String) -> Unit,
    onAddFlowClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        androidx.compose.foundation.layout.Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TapTargets.minimum)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(flows, key = { it.id }) { flow ->
                        FlowTab(
                            flow = flow,
                            active = flow.id == activeFlowId,
                            onClick = { onFlowClick(flow.id) },
                        )
                    }
                    item(key = "__add_flow") {
                        OutlinedButton(
                            onClick = onAddFlowClick,
                            modifier = Modifier
                                .heightIn(min = TapTargets.minimum)
                                .testTag("flow_tab_add"),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = Spacing.xs))
                            Text("Добавить")
                        }
                    }
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .heightIn(min = TapTargets.minimum)
                        .testTag("settings_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Настройки",
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowTab(
    flow: FlowDescriptor,
    active: Boolean,
    onClick: () -> Unit,
) {
    if (active) {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier
                .heightIn(min = TapTargets.minimum)
                .testTag("flow_tab_${flow.id}"),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(flow.name, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .heightIn(min = TapTargets.minimum)
                .testTag("flow_tab_${flow.id}"),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(flow.name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

