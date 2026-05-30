package com.launcher.api.edit

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ElementId
import com.launcher.api.config.Flow
import com.launcher.api.config.Slot
import com.launcher.api.result.Outcome

/**
 * Pure-function domain verbs for tile editing (FR-001).
 *
 * Each verb applies a [TileEditOperation] against a [ConfigDocument], returning
 * a new [ConfigDocument] на success или an [EditError] on validation failure.
 *
 * Idempotency: applying the same op twice yields the same result (Move with
 * already-at-position is a no-op; Add appends regardless; Remove of missing
 * slot fails fast with [EditError.SlotNotFound]). `ConfigEditor.pushPending`
 * (спека 008) handles cross-device dedup.
 *
 * Pure-Kotlin (no platform / SDK / transport types). Konsist gate T170
 * enforces.
 */
object TileEditOperations {

    /**
     * Applies the [op] to [config] and returns the updated document or a typed
     * error. Original [config] is not mutated (immutable transformation).
     */
    fun apply(
        op: TileEditOperation,
        config: ConfigDocument,
    ): Outcome<ConfigDocument, EditError> = when (op) {
        is TileEditOperation.Add -> addSlot(op.flowId, op.slot, config)
        is TileEditOperation.Move -> moveSlot(op.flowId, op.slotId, op.newPosition, config)
        is TileEditOperation.Remove -> removeSlot(op.flowId, op.slotId, config)
        is TileEditOperation.Replace -> replaceSlot(op.flowId, op.slotId, op.newSlot, config)
    }

    /** Convenience: append [slot] to the end of [flowId]'s slot list. */
    fun addSlot(
        flowId: ElementId,
        slot: Slot,
        config: ConfigDocument,
    ): Outcome<ConfigDocument, EditError> = withFlow(config, flowId) { flow ->
        Outcome.Success(flow.copy(slots = flow.slots + slot))
    }

    /**
     * Convenience: move existing [slotId] within [flowId] to [newPosition].
     * If the slot is already at [newPosition], returns [config] unchanged
     * (Success). [newPosition] is bounded `0..slots.size - 1`.
     */
    fun moveSlot(
        flowId: ElementId,
        slotId: ElementId,
        newPosition: Int,
        config: ConfigDocument,
    ): Outcome<ConfigDocument, EditError> = withFlow(config, flowId) { flow ->
        val currentIndex = flow.slots.indexOfFirst { it.id == slotId }
        if (currentIndex < 0) {
            return@withFlow Outcome.Failure(EditError.SlotNotFound(slotId.value))
        }
        if (newPosition !in 0 until flow.slots.size) {
            return@withFlow Outcome.Failure(EditError.InvalidPosition)
        }
        if (newPosition == currentIndex) {
            return@withFlow Outcome.Success(flow)
        }
        val mutable = flow.slots.toMutableList()
        val removed = mutable.removeAt(currentIndex)
        mutable.add(newPosition, removed)
        Outcome.Success(flow.copy(slots = mutable.toList()))
    }

    /** Convenience: remove [slotId] from [flowId]. */
    fun removeSlot(
        flowId: ElementId,
        slotId: ElementId,
        config: ConfigDocument,
    ): Outcome<ConfigDocument, EditError> = withFlow(config, flowId) { flow ->
        val filtered = flow.slots.filterNot { it.id == slotId }
        if (filtered.size == flow.slots.size) {
            Outcome.Failure(EditError.SlotNotFound(slotId.value))
        } else {
            Outcome.Success(flow.copy(slots = filtered))
        }
    }

    /** Convenience: replace [slotId] in [flowId] with [newSlot]. */
    fun replaceSlot(
        flowId: ElementId,
        slotId: ElementId,
        newSlot: Slot,
        config: ConfigDocument,
    ): Outcome<ConfigDocument, EditError> = withFlow(config, flowId) { flow ->
        val index = flow.slots.indexOfFirst { it.id == slotId }
        if (index < 0) {
            Outcome.Failure(EditError.SlotNotFound(slotId.value))
        } else {
            val mutable = flow.slots.toMutableList()
            mutable[index] = newSlot
            Outcome.Success(flow.copy(slots = mutable.toList()))
        }
    }

    /** Locate [flowId] in [config], run [transform], and rebuild config. */
    private inline fun withFlow(
        config: ConfigDocument,
        flowId: ElementId,
        transform: (Flow) -> Outcome<Flow, EditError>,
    ): Outcome<ConfigDocument, EditError> {
        val flowIndex = config.flows.indexOfFirst { it.id == flowId }
        if (flowIndex < 0) {
            return Outcome.Failure(EditError.FlowNotFound(flowId.value))
        }
        return when (val r = transform(config.flows[flowIndex])) {
            is Outcome.Failure -> r
            is Outcome.Success -> {
                val newFlows = config.flows.toMutableList()
                newFlows[flowIndex] = r.value
                Outcome.Success(config.copy(flows = newFlows.toList()))
            }
        }
    }
}
