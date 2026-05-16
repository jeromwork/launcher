package com.launcher.ui.admin

/**
 * Action callbacks for [EditorScreen] (spec 009 FR-005..FR-015). Plain
 * data class of lambdas — Compose-side state hoist pattern; ViewModel
 * wires implementations.
 *
 * Stub default impl makes preview / tests easy without wiring a VM:
 * `EditorActions()` returns a no-op for every callback.
 */
data class EditorActions(
    /** Short tap on a tile — preview action (FR-005). */
    val onSlotTap: (slotId: String) -> Unit = {},
    /** Long-press — start drag (Phase 9) or open menu (FR-008). */
    val onSlotLongPress: (slotId: String) -> Unit = {},
    /** "···" alternative menu (FR-009 / FR-A11Y-004). */
    val onSlotEditMenu: (slotId: String) -> Unit = {},
    /** Publish CTA — triggers ConfigPublishUseCase chain (FR-015, spec 008). */
    val onPublish: () -> Unit = {},
    /** Toggle View ↔ Edit mode (FR-005). */
    val onToggleMode: () -> Unit = {},
    /** Open history viewer for this link (FR-037). */
    val onHistoryClick: () -> Unit = {},
    /**
     * Drop-target callback: re-order [slotId] from [fromFlowId] to be at
     * position [toIndex] inside [toFlowId] (G5 — DnD reorder).
     */
    val onReorder: (fromFlowId: String, slotId: String, toFlowId: String, toIndex: Int) -> Unit = { _, _, _, _ -> },
)
