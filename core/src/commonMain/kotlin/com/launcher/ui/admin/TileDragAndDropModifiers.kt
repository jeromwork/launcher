package com.launcher.ui.admin

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.launcher.api.config.ElementId

/**
 * Compose-side `Modifier` factories that wire a [TileCard] (source) and a
 * drop zone (target) into a [TileDragAndDropState] (spec 009 FR-008).
 *
 * Plan §7 R1: chose `pointerInput { detectDragGesturesAfterLongPress }` over
 * `Modifier.dragAndDropSource` because (a) the latter is experimental on
 * foundation 1.6 and requires a platform-specific `ClipData` shim that
 * leaks Android types via `DragAndDropTransferData` — would break commonMain
 * isolation rule 1; (b) FR-008 two-way door explicitly permits this
 * fallback; (c) NFR-001 frame-budget verification on Pixel 4a class
 * hardware still gates real-world activation — but the gesture wiring
 * itself works on any Compose target.
 *
 * Flow:
 *  1. Long-press a tile → [TileDragAndDropState.startDrag] fires.
 *  2. Pointer position is tracked in [TileDragAndDropState.dragPosition].
 *  3. Each drop target reports its bounds via [tileDropTarget]'s
 *     onGloballyPositioned; on drag release we resolve the target that
 *     contains the final pointer position and call its `onDrop` callback.
 *
 * TODO(physical-device): NFR-001 frame-budget verification needs real
 * hardware (Pixel 4a class) — локально на ноутбуке не проверить.
 */
@Composable
fun rememberTileDragAndDropState(): TileDragAndDropState = remember { TileDragAndDropState() }

fun Modifier.tileDragSource(
    state: TileDragAndDropState,
    slotId: ElementId,
    fromFlowId: ElementId,
    fromIndex: Int,
): Modifier = this.pointerInput(state, slotId, fromFlowId, fromIndex) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            state.startDrag(slotId, fromFlowId, fromIndex, offset)
        },
        onDrag = { change, _ ->
            change.consume()
            state.updateDrag(change.position)
        },
        onDragEnd = { state.finishDrag() },
        onDragCancel = { state.cancelDrag() },
    )
}

fun Modifier.tileDropTarget(
    state: TileDragAndDropState,
    targetId: TileDropTargetId,
    onDrop: (DragInProgress) -> Unit,
): Modifier = this.onGloballyPositioned { layoutCoordinates ->
    val pos = layoutCoordinates.positionInRoot()
    val size = layoutCoordinates.size
    val rect = Rect(
        left = pos.x,
        top = pos.y,
        right = pos.x + size.width,
        bottom = pos.y + size.height,
    )
    state.registerDropTarget(targetId, rect, onDrop)
}
