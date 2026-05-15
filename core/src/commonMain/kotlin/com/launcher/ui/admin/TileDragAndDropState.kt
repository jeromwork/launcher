package com.launcher.ui.admin

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.launcher.api.config.ElementId

/**
 * State + intent surface for tile drag-and-drop (spec 009 FR-008, NFR-001).
 *
 * Plan §7 R1 + plan §11: Compose `Modifier.dragAndDropSource/Target` API
 * (foundation 1.6+) is the chosen primary path; if frame-budget gate
 * NFR-001 (0 dropped frames Pixel 4a class p99 < 16 ms) fails, fall
 * back to `pointerInput` (two-way door per FR-008).
 *
 * This file ships the **state** + **intents** so the wire-up between
 * EditorViewModel ↔ Compose dragAndDrop is plumbing-only when Phase 9
 * physical-device testing wraps. The actual `Modifier.dragAndDropSource`
 * invocation lives in [TileDragAndDropModifiers] (Compose Multiplatform
 * stable API).
 *
 * TODO(physical-device): NFR-001 macrobenchmark — `androidx.benchmark.macro`
 * FrameTimingMetric for drag scenario on Pixel 4a class. p99 < 16ms across
 * 20 drag operations. Локально на ноутбуке не проверить.
 */
@Stable
class TileDragAndDropState {
    private val _activeDrag = mutableStateOf<DragInProgress?>(null)
    val activeDrag: DragInProgress? get() = _activeDrag.value

    fun startDrag(slotId: ElementId, fromFlowId: ElementId, fromIndex: Int) {
        _activeDrag.value = DragInProgress(
            slotId = slotId,
            fromFlowId = fromFlowId,
            fromIndex = fromIndex,
        )
    }

    fun cancelDrag() {
        _activeDrag.value = null
    }

    /**
     * Drop event — caller is the target zone. Returns the [DragInProgress]
     * that was active so callers can dispatch the move; or null if no
     * drag was in progress (defensive against duplicate drop events).
     */
    fun consumeDrop(): DragInProgress? {
        val active = _activeDrag.value
        _activeDrag.value = null
        return active
    }
}

@Immutable
data class DragInProgress(
    val slotId: ElementId,
    val fromFlowId: ElementId,
    val fromIndex: Int,
)
