package com.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.ui.components.TileCard
import com.launcher.ui.navigation.FlowComponent
import com.launcher.ui.theme.Spacing

/**
 * Renders one flow's slot grid.
 *
 * Spec 005 migration note: confirmation/warning overlays from spec 002 are
 * **temporarily removed**. Phase 5 (US-508) brings back failure feedback as a
 * snackbar attached at this level. Placeholder slots (slot.action == null)
 * still render but tap is a no-op.
 */
@Composable
fun FlowScreen(
    component: FlowComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (state.slots.isEmpty()) {
            EmptyFlow(state.flowName)
        } else {
            LazyVerticalGrid(
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

@Composable
private fun EmptyFlow(flowName: String) {
    Text(
        text = if (flowName.isEmpty()) "Нет слотов." else "$flowName: пока пусто.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.xl),
    )
}
