package com.launcher.ui.admin

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.launcher.api.config.ElementId

/**
 * State + intent surface for tile drag-and-drop (spec 009 FR-008, NFR-001).
 *
 * Plan §7 R1: `pointerInput { detectDragGesturesAfterLongPress }` chosen
 * over `Modifier.dragAndDropSource` per FR-008 two-way door — keeps
 * commonMain free of Android `ClipData` / `DragAndDropTransferData` types
 * (CLAUDE.md rule 1) и avoids experimental opt-in on foundation 1.6.
 *
 * Drop targets register their bounds on every layout via
 * [tileDropTarget]; on drag release [finishDrag] resolves the target that
 * contains the current pointer position and invokes its callback.
 */
@Stable
class TileDragAndDropState {
    private val _activeDrag = mutableStateOf<DragInProgress?>(null)
    val activeDrag: DragInProgress? get() = _activeDrag.value

    private val _dragPosition = mutableStateOf<Offset?>(null)
    val dragPosition: Offset? get() = _dragPosition.value

    private val dropTargets = mutableMapOf<TileDropTargetId, RegisteredDropTarget>()

    fun startDrag(slotId: ElementId, fromFlowId: ElementId, fromIndex: Int, position: Offset) {
        _activeDrag.value = DragInProgress(
            slotId = slotId,
            fromFlowId = fromFlowId,
            fromIndex = fromIndex,
        )
        _dragPosition.value = position
    }

    fun updateDrag(position: Offset) {
        if (_activeDrag.value != null) {
            val current = _dragPosition.value ?: Offset.Zero
            _dragPosition.value = current + position
        }
    }

    fun cancelDrag() {
        _activeDrag.value = null
        _dragPosition.value = null
    }

    /**
     * Drop event fired by [tileDragSource] on pointer release. Resolves
     * the drop target containing the current pointer position and invokes
     * its callback with the in-flight drag payload.
     */
    fun finishDrag() {
        val active = _activeDrag.value
        val position = _dragPosition.value
        _activeDrag.value = null
        _dragPosition.value = null
        if (active == null || position == null) return
        val hit = dropTargets.values.firstOrNull { it.bounds.contains(position) }
        hit?.onDrop?.invoke(active)
    }

    /** Registered by [tileDropTarget] on every layout pass. */
    fun registerDropTarget(
        id: TileDropTargetId,
        bounds: Rect,
        onDrop: (DragInProgress) -> Unit,
    ) {
        dropTargets[id] = RegisteredDropTarget(bounds = bounds, onDrop = onDrop)
    }

    /** Disambiguator for tests + housekeeping; not called from UI. */
    fun clearDropTargets() {
        dropTargets.clear()
    }
}

@Immutable
data class DragInProgress(
    val slotId: ElementId,
    val fromFlowId: ElementId,
    val fromIndex: Int,
)

/**
 * Stable identifier for a registered drop target. Use a stable type
 * (slot id, "trash", "before-flow-X") so target re-registration via
 * onGloballyPositioned doesn't accumulate stale entries.
 */
@Immutable
sealed interface TileDropTargetId {
    data class Slot(val slotId: ElementId) : TileDropTargetId
    data class FlowEdge(val flowId: ElementId, val edge: Edge) : TileDropTargetId {
        enum class Edge { Before, After }
    }
    data object Trash : TileDropTargetId
}

private data class RegisteredDropTarget(
    val bounds: Rect,
    val onDrop: (DragInProgress) -> Unit,
)
