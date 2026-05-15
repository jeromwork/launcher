package com.launcher.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.launcher.api.config.ElementId

/**
 * Compose-side `Modifier` factories that wire a [TileCard] (source) and a
 * drop zone (target) into a [TileDragAndDropState] (spec 009 FR-008).
 *
 * Spec-9 ships a **stateful scaffold** today — the actual gesture wiring
 * via `Modifier.dragAndDropSource` / `Modifier.dragAndDropTarget`
 * (foundation 1.7+ stable, foundation 1.6 has experimental) is annotated
 * but commented out so the codebase compiles on the current foundation
 * baseline. Activated as part of Phase 9 verification on an emulator.
 *
 * TODO(emulator-verify): activate `Modifier.dragAndDropSource` /
 * `Modifier.dragAndDropTarget` calls during NFR-001 macrobenchmark
 * (frame budget gate). If frame budget fails on Pixel 4a class →
 * fall back to `Modifier.pointerInput { detectDragGestures }` per
 * FR-008 two-way door.
 *
 * TODO(physical-device): NFR-001 frame-budget verification needs real
 * hardware (Pixel 4a class) — локально на ноутбуке не проверить.
 */
@Composable
fun rememberTileDragAndDropState(): TileDragAndDropState = androidx.compose.runtime.remember { TileDragAndDropState() }

/**
 * Returns a modifier that marks the receiver as a drag source for the
 * given slot. Phase 9 emulator verification will replace the body with
 * `Modifier.dragAndDropSource { offset -> ... }`. Currently no-op so
 * the editor still compiles + functional (drag is the **alternative**
 * channel; "···" menu (FR-009 / FR-A11Y-004) keeps editor usable).
 */
fun Modifier.tileDragSource(
    @Suppress("UNUSED_PARAMETER") state: TileDragAndDropState,
    @Suppress("UNUSED_PARAMETER") slotId: ElementId,
    @Suppress("UNUSED_PARAMETER") fromFlowId: ElementId,
    @Suppress("UNUSED_PARAMETER") fromIndex: Int,
): Modifier = this  // TODO(emulator-verify Phase 9): wire dragAndDropSource

/**
 * Returns a modifier that marks the receiver as a drop target. On
 * successful drop, [onDrop] is invoked with the drag-in-progress payload.
 * Currently no-op for the same reason as [tileDragSource].
 */
fun Modifier.tileDropTarget(
    @Suppress("UNUSED_PARAMETER") state: TileDragAndDropState,
    @Suppress("UNUSED_PARAMETER") onDrop: (DragInProgress) -> Unit,
): Modifier = this  // TODO(emulator-verify Phase 9): wire dragAndDropTarget
