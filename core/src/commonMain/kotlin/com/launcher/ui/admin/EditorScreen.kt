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
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            ModeBanner(state.mode)
            ConflictBanner(hasConflict = state.mergeConflict != null)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    for (flow in state.draft.flows) {
                        items(items = flow.slots, key = { it.id.value }) { slot: Slot ->
                            TileCard(
                                label = slotLabel(slot),
                                onClick = { actions.onSlotTap(slot.id.value) },
                                slotKind = slot.kind,
                                editMode = state.mode == AdminEditorMode.Edit,
                                onLongPress = { actions.onSlotLongPress(slot.id.value) },
                                onEditMenuClick = { actions.onSlotEditMenu(slot.id.value) },
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
private fun ModeBanner(mode: AdminEditorMode) {
    val text = when (mode) {
        AdminEditorMode.View -> "Просмотр"
        AdminEditorMode.Edit -> "Редактирование"
    }
    Text(
        text = text,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
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
