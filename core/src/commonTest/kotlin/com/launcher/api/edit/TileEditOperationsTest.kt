package com.launcher.api.edit

import com.launcher.api.config.ElementId
import com.launcher.api.edit.TileEditTestFixtures.FLOW_A_ID
import com.launcher.api.edit.TileEditTestFixtures.FLOW_B_ID
import com.launcher.api.edit.TileEditTestFixtures.SLOT_1_ID
import com.launcher.api.edit.TileEditTestFixtures.SLOT_2_ID
import com.launcher.api.edit.TileEditTestFixtures.SLOT_3_ID
import com.launcher.api.edit.TileEditTestFixtures.SLOT_4_ID
import com.launcher.api.edit.TileEditTestFixtures.configWithThreeSlots
import com.launcher.api.edit.TileEditTestFixtures.openAppSlot
import com.launcher.api.result.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [TileEditOperations] — covers FR-001 (domain verbs over
 * ConfigDocument.Flow.slots[]).
 *
 * Test matrix:
 *  - Add: valid; flow-not-found.
 *  - Move: valid forward; valid backward; same-position no-op; slot-not-found;
 *    invalid-position (negative); invalid-position (>= size); flow-not-found.
 *  - Remove: valid; slot-not-found; flow-not-found.
 *  - Replace: valid; slot-not-found; flow-not-found.
 *
 * Trace: spec 014 T025.
 */
class TileEditOperationsTest {

    // ─── Add ──────────────────────────────────────────────────────────────

    @Test
    fun add_appends_slot_to_flow() {
        val config = configWithThreeSlots()
        val newSlot = openAppSlot(SLOT_4_ID, "com.example.four")

        val result = TileEditOperations.addSlot(FLOW_A_ID, newSlot, config)

        assertTrue(result is Outcome.Success, "add should succeed: $result")
        val updated = (result as Outcome.Success).value
        assertEquals(4, updated.flows[0].slots.size)
        assertEquals(SLOT_4_ID, updated.flows[0].slots[3].id)
    }

    @Test
    fun add_returns_FlowNotFound_when_flow_id_unknown() {
        val config = configWithThreeSlots()
        val unknown = ElementId("99999999-9999-4999-8999-999999999999")
        val newSlot = openAppSlot(SLOT_4_ID)

        val result = TileEditOperations.addSlot(unknown, newSlot, config)

        assertEquals(Outcome.Failure(EditError.FlowNotFound(unknown.value)), result)
    }

    @Test
    fun add_does_not_mutate_original_config() {
        val config = configWithThreeSlots()
        val originalSize = config.flows[0].slots.size

        TileEditOperations.addSlot(FLOW_A_ID, openAppSlot(SLOT_4_ID), config)

        assertEquals(originalSize, config.flows[0].slots.size)
    }

    // ─── Move ─────────────────────────────────────────────────────────────

    @Test
    fun move_relocates_slot_forward() {
        val config = configWithThreeSlots()

        val result = TileEditOperations.moveSlot(FLOW_A_ID, SLOT_1_ID, newPosition = 2, config)

        assertTrue(result is Outcome.Success)
        val slots = (result as Outcome.Success).value.flows[0].slots
        assertEquals(listOf(SLOT_2_ID, SLOT_3_ID, SLOT_1_ID), slots.map { it.id })
    }

    @Test
    fun move_relocates_slot_backward() {
        val config = configWithThreeSlots()

        val result = TileEditOperations.moveSlot(FLOW_A_ID, SLOT_3_ID, newPosition = 0, config)

        assertTrue(result is Outcome.Success)
        val slots = (result as Outcome.Success).value.flows[0].slots
        assertEquals(listOf(SLOT_3_ID, SLOT_1_ID, SLOT_2_ID), slots.map { it.id })
    }

    @Test
    fun move_to_same_position_is_no_op() {
        val config = configWithThreeSlots()
        val originalOrder = config.flows[0].slots.map { it.id }

        val result = TileEditOperations.moveSlot(FLOW_A_ID, SLOT_2_ID, newPosition = 1, config)

        assertTrue(result is Outcome.Success)
        val slots = (result as Outcome.Success).value.flows[0].slots
        assertEquals(originalOrder, slots.map { it.id })
    }

    @Test
    fun move_returns_SlotNotFound_when_slot_id_unknown() {
        val config = configWithThreeSlots()
        val unknown = ElementId("99999999-9999-4999-8999-999999999999")

        val result = TileEditOperations.moveSlot(FLOW_A_ID, unknown, newPosition = 0, config)

        assertEquals(Outcome.Failure(EditError.SlotNotFound(unknown.value)), result)
    }

    @Test
    fun move_returns_InvalidPosition_when_position_negative() {
        val config = configWithThreeSlots()

        val result = TileEditOperations.moveSlot(FLOW_A_ID, SLOT_1_ID, newPosition = -1, config)

        assertEquals(Outcome.Failure(EditError.InvalidPosition), result)
    }

    @Test
    fun move_returns_InvalidPosition_when_position_out_of_bounds() {
        val config = configWithThreeSlots() // 3 slots → valid range 0..2

        val result = TileEditOperations.moveSlot(FLOW_A_ID, SLOT_1_ID, newPosition = 3, config)

        assertEquals(Outcome.Failure(EditError.InvalidPosition), result)
    }

    @Test
    fun move_returns_FlowNotFound_when_flow_unknown() {
        val config = configWithThreeSlots()

        val result = TileEditOperations.moveSlot(FLOW_B_ID, SLOT_1_ID, newPosition = 0, config)

        assertEquals(Outcome.Failure(EditError.FlowNotFound(FLOW_B_ID.value)), result)
    }

    // ─── Remove ───────────────────────────────────────────────────────────

    @Test
    fun remove_drops_slot_from_flow() {
        val config = configWithThreeSlots()

        val result = TileEditOperations.removeSlot(FLOW_A_ID, SLOT_2_ID, config)

        assertTrue(result is Outcome.Success)
        val slots = (result as Outcome.Success).value.flows[0].slots
        assertEquals(listOf(SLOT_1_ID, SLOT_3_ID), slots.map { it.id })
    }

    @Test
    fun remove_returns_SlotNotFound_when_slot_unknown() {
        val config = configWithThreeSlots()
        val unknown = ElementId("99999999-9999-4999-8999-999999999999")

        val result = TileEditOperations.removeSlot(FLOW_A_ID, unknown, config)

        assertEquals(Outcome.Failure(EditError.SlotNotFound(unknown.value)), result)
    }

    @Test
    fun remove_returns_FlowNotFound_when_flow_unknown() {
        val config = configWithThreeSlots()

        val result = TileEditOperations.removeSlot(FLOW_B_ID, SLOT_1_ID, config)

        assertEquals(Outcome.Failure(EditError.FlowNotFound(FLOW_B_ID.value)), result)
    }

    // ─── Replace ──────────────────────────────────────────────────────────

    @Test
    fun replace_swaps_slot_at_same_position() {
        val config = configWithThreeSlots()
        val replacement = openAppSlot(SLOT_4_ID, "com.replacement.app")

        val result = TileEditOperations.replaceSlot(FLOW_A_ID, SLOT_2_ID, replacement, config)

        assertTrue(result is Outcome.Success)
        val slots = (result as Outcome.Success).value.flows[0].slots
        assertEquals(3, slots.size)
        assertEquals(SLOT_4_ID, slots[1].id) // position 1 (was SLOT_2) replaced
        assertEquals(SLOT_1_ID, slots[0].id)
        assertEquals(SLOT_3_ID, slots[2].id)
    }

    @Test
    fun replace_returns_SlotNotFound_when_slot_unknown() {
        val config = configWithThreeSlots()
        val unknown = ElementId("99999999-9999-4999-8999-999999999999")

        val result = TileEditOperations.replaceSlot(
            FLOW_A_ID, unknown, openAppSlot(SLOT_4_ID), config,
        )

        assertEquals(Outcome.Failure(EditError.SlotNotFound(unknown.value)), result)
    }

    // ─── Polymorphic apply() entry point ──────────────────────────────────

    @Test
    fun apply_dispatches_to_add() {
        val config = configWithThreeSlots()
        val op = TileEditOperation.Add(FLOW_A_ID, openAppSlot(SLOT_4_ID))

        val result = TileEditOperations.apply(op, config)

        assertTrue(result is Outcome.Success)
        assertEquals(4, (result as Outcome.Success).value.flows[0].slots.size)
    }

    @Test
    fun apply_dispatches_to_remove() {
        val config = configWithThreeSlots()
        val op = TileEditOperation.Remove(FLOW_A_ID, SLOT_2_ID)

        val result = TileEditOperations.apply(op, config)

        assertTrue(result is Outcome.Success)
        assertEquals(2, (result as Outcome.Success).value.flows[0].slots.size)
    }

    @Test
    fun apply_dispatches_to_move() {
        val config = configWithThreeSlots()
        val op = TileEditOperation.Move(FLOW_A_ID, SLOT_1_ID, newPosition = 2)

        val result = TileEditOperations.apply(op, config)

        assertTrue(result is Outcome.Success)
        assertEquals(
            SLOT_1_ID,
            (result as Outcome.Success).value.flows[0].slots[2].id,
        )
    }

    @Test
    fun apply_dispatches_to_replace() {
        val config = configWithThreeSlots()
        val op = TileEditOperation.Replace(FLOW_A_ID, SLOT_2_ID, openAppSlot(SLOT_4_ID))

        val result = TileEditOperations.apply(op, config)

        assertTrue(result is Outcome.Success)
        assertEquals(
            SLOT_4_ID,
            (result as Outcome.Success).value.flows[0].slots[1].id,
        )
    }
}
