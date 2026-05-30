package com.launcher.api.edit

import com.launcher.api.config.ElementId
import com.launcher.api.config.Slot

/**
 * Domain operation на `ConfigDocument.flows[].slots[]` (FR-001).
 *
 * Applied via [TileEditOperations.apply], returning
 * `Outcome<ConfigDocument, EditError>`. Each operation is idempotent at
 * `ConfigDocument` level — applying the same op twice yields the same result;
 * `ConfigEditor.pushPending` performs server-side dedup per спека 008.
 *
 * Pure-Kotlin (no platform / SDK / transport types). Konsist gate T170
 * enforces.
 */
sealed class TileEditOperation {
    /** Flow being modified — all variants need it. */
    abstract val flowId: ElementId

    /** Add a new slot to the end of [flowId]'s slot list. */
    data class Add(
        override val flowId: ElementId,
        val slot: Slot,
    ) : TileEditOperation()

    /** Move existing [slotId] within [flowId] to [newPosition] (0-based index). */
    data class Move(
        override val flowId: ElementId,
        val slotId: ElementId,
        val newPosition: Int,
    ) : TileEditOperation()

    /** Remove [slotId] from [flowId]. */
    data class Remove(
        override val flowId: ElementId,
        val slotId: ElementId,
    ) : TileEditOperation()

    /** Replace [slotId] in [flowId] with [newSlot] (id preserved or replaced). */
    data class Replace(
        override val flowId: ElementId,
        val slotId: ElementId,
        val newSlot: Slot,
    ) : TileEditOperation()
}
