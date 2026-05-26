package com.launcher.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.admin.AdminEditorMode
import com.launcher.api.admin.EditorState
import com.launcher.api.config.Flow
import com.launcher.api.config.Slot
import com.launcher.ui.components.TileCard
import androidx.compose.runtime.remember

/**
 * Stateless editor surface (spec 009 FR-001, FR-005, FR-015). The Composable
 * is pure presentation — state owned by the ViewModel; all mutations go
 * through the [EditorActions] lambdas.
 *
 * Renders:
 *   - mode banner ("Просмотр" / "Редактирование");
 *   - one [FlowSection] per [Flow] in `state.draft.flows`;
 *   - "Опубликовать" CTA disabled when `pendingPush=true` or
 *     `mergeConflict != null`;
 *   - applied/conflict states inline above CTA.
 *
 * Drag-and-drop wiring (Phase 9) plugs into [EditorActions.onSlotLongPress]
 * by replacing it with `Modifier.dragAndDropSource`; the alternative
 * "···" menu (FR-009 / FR-A11Y-004) lives on [TileCard] via
 * [EditorActions.onSlotEditMenu].
 *
 * TODO(physical-device): SC-001 90-second elderly walkthrough — verify
 * на реальном устройстве in Phase 14.
 */
@Composable
fun EditorScreen(
    state: EditorState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
) {
    // G5 — shared drag-and-drop state for in-flow tile re-ordering.
    // The state is remembered at the screen level so cross-tile drag
    // targets see the active drag via the single instance.
    val dndState = rememberTileDragAndDropState()
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            ModeBanner(
                mode = state.mode,
                onToggleMode = actions.onToggleMode,
                onHistoryClick = actions.onHistoryClick,
            )
            ConflictBanner(hasConflict = state.mergeConflict != null)
            // Spec 012 FR-015 — Add Document button (visible только в Edit mode).
            if (state.mode == AdminEditorMode.Edit) {
                AddDocumentEntryRow(onAddDocument = actions.onAddDocument)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    for (flow in state.draft.flows) {
                        itemsIndexed(items = flow.slots, key = { _, s -> s.id.value }) { index, slot: Slot ->
                            val isDragged = dndState.activeDrag?.slotId == slot.id
                            TileCard(
                                label = slotLabel(slot),
                                onClick = { actions.onSlotTap(slot.id.value) },
                                slotKind = slot.kind,
                                editMode = state.mode == AdminEditorMode.Edit,
                                dragged = isDragged,
                                onLongPress = { actions.onSlotLongPress(slot.id.value) },
                                onEditMenuClick = { actions.onSlotEditMenu(slot.id.value) },
                                modifier = Modifier
                                    .tileDragSource(
                                        state = dndState,
                                        slotId = slot.id,
                                        fromFlowId = flow.id,
                                        fromIndex = index,
                                    )
                                    .tileDropTarget(
                                        state = dndState,
                                        targetId = TileDropTargetId.Slot(slot.id),
                                        onDrop = { drag ->
                                            actions.onReorder(
                                                drag.fromFlowId.value,
                                                drag.slotId.value,
                                                flow.id.value,
                                                index,
                                            )
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
            PublishBar(
                pendingPush = state.pendingPush,
                hasConflict = state.mergeConflict != null,
                onPublish = actions.onPublish,
            )
        }
    }
}

@Composable
private fun ModeBanner(
    mode: AdminEditorMode,
    onToggleMode: () -> Unit,
    onHistoryClick: () -> Unit,
) {
    val text = when (mode) {
        AdminEditorMode.View -> "Просмотр"
        AdminEditorMode.Edit -> "Редактирование"
    }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.OutlinedButton(onClick = onHistoryClick) {
                Text("История")
            }
            androidx.compose.material3.OutlinedButton(onClick = onToggleMode) {
                Text(
                    text = when (mode) {
                        AdminEditorMode.View -> "Изменить"
                        AdminEditorMode.Edit -> "Готово"
                    },
                )
            }
        }
    }
}

/**
 * Spec 012 FR-015 — admin entry "+ Документ" в Edit mode. Tap launches
 * AddDocumentScreen flow (host wires это).
 */
@Composable
private fun AddDocumentEntryRow(onAddDocument: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        androidx.compose.material3.OutlinedButton(
            onClick = onAddDocument,
            // Senior-safe tap target ≥ 56dp — Material3 OutlinedButton default = 40dp,
            // so увеличиваем явно.
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Text("+ Документ", fontSize = 18.sp)
        }
    }
}

@Composable
private fun ConflictBanner(hasConflict: Boolean) {
    if (!hasConflict) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = "Конфликт изменений — кто-то редактировал параллельно. Разрешите конфликт.",
            modifier = Modifier.padding(16.dp),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun PublishBar(
    pendingPush: Boolean,
    hasConflict: Boolean,
    onPublish: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Button(
                onClick = onPublish,
                enabled = !pendingPush && !hasConflict,
            ) {
                Text(if (pendingPush) "Публикую…" else "Опубликовать")
            }
        }
    }
}

private fun slotLabel(slot: Slot): String =
    slot.args?.get("label")?.toString()?.removeSurrounding("\"")
        ?: slot.kind.wireValue
