package com.launcher.api.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ConfigDiff tests covering FR-051..054 + SC-007 (US-2 acceptance scenarios 1-5).
 *
 * Per data-model.md, diff is "merge server into local" direction:
 * - addedFlows = present в server, absent в local → server has new ones we need;
 * - removedFlowIds = present в local, absent в server → server removed them;
 * - modifiedFlows = same id, different content → conflict needing user choice.
 */
class ConfigDiffTest {

    @Test
    fun identical_configs_yield_empty_diff_FR_052() {
        // US-2 scenario 4 ("both deleted same element") collapses into identical
        // post-state → empty diff → push proceeds без merge UI.
        val a = sampleConfig(presetId = "simple-launcher", flows = listOf(sampleFlow("f1")), contacts = emptyList())
        val b = sampleConfig(presetId = "simple-launcher", flows = listOf(sampleFlow("f1")), contacts = emptyList())

        val diff = ConfigDiff.compute(local = a, server = b)
        assertTrue(diff.isEmpty, "Diff should be empty for identical configs, got: $diff")
        assertFalse(diff.hasOverlappingChanges)
    }

    @Test
    fun non_overlapping_changes_FR_053_auto_merge() {
        // US-2 scenario 3 — local added flow X, server added flow Y (different ids).
        // Auto-mergeable: добавить обе. Diff is NOT empty, но не overlapping.
        val flowX = sampleFlow("f1111111-1111-4111-8111-111111111111")
        val flowY = sampleFlow("f2222222-2222-4222-8222-222222222222")
        val local = sampleConfig(flows = listOf(flowX))
        val server = sampleConfig(flows = listOf(flowY))

        val diff = ConfigDiff.compute(local = local, server = server)
        assertFalse(diff.isEmpty, "Should have diff: local has flowX, server has flowY")
        assertFalse(diff.hasOverlappingChanges, "No id collisions → no overlap")
        // From server's perspective: flowY is added (server has it, we don't),
        // flowX is removed (we have it, server doesn't).
        assertEquals(1, diff.addedFlows.size)
        assertEquals(flowY.id, diff.addedFlows.first().id)
        assertEquals(1, diff.removedFlowIds.size)
        assertEquals(flowX.id, diff.removedFlowIds.first())
        assertEquals(0, diff.modifiedFlows.size)
    }

    @Test
    fun overlapping_modification_same_id_different_content_FR_051() {
        // US-2 scenario 1 — оба touched same flow id with different content.
        // Diff records as `modifiedFlows` — merge UI shows local-vs-server.
        val id = ElementId("f1111111-1111-4111-8111-111111111111")
        val flowLocal = Flow(id = id, title = "Маша (мама)", slots = emptyList())
        val flowServer = Flow(id = id, title = "Маша (бабушка)", slots = emptyList())
        val local = sampleConfig(flows = listOf(flowLocal))
        val server = sampleConfig(flows = listOf(flowServer))

        val diff = ConfigDiff.compute(local = local, server = server)
        assertFalse(diff.isEmpty)
        assertTrue(diff.hasOverlappingChanges, "Modified same id = overlap")
        assertEquals(1, diff.modifiedFlows.size)
        val mod = diff.modifiedFlows.first()
        assertEquals(id, mod.id)
        assertEquals("Маша (мама)", mod.local.title)
        assertEquals("Маша (бабушка)", mod.server.title)
    }

    @Test
    fun deletion_vs_modification_same_id_US_2_scenario_5() {
        // US-2 scenario 5 — local deleted X, server modified X. From server's
        // perspective: X is present в server (modified), absent в local → addedFlows.
        // From local's perspective (NOT what we compute): X removed.
        // The current diff direction ("merge server into local") shows X as added
        // because server has it и local doesn't. User decides: «restore the
        // server's version» (accept addedFlow) or «complete the delete» (reject).
        val id = ElementId("f1111111-1111-4111-8111-111111111111")
        val flowServer = Flow(id = id, title = "modified", slots = emptyList())
        val local = sampleConfig(flows = emptyList()) // deleted
        val server = sampleConfig(flows = listOf(flowServer)) // modified

        val diff = ConfigDiff.compute(local = local, server = server)
        // Server still has the element, local lost it (deletion). Diff says: server
        // adds it back from local's perspective.
        assertEquals(1, diff.addedFlows.size, "Server-modified-still-exists shows как added: $diff")
        assertEquals(0, diff.modifiedFlows.size)
        assertFalse(diff.hasOverlappingChanges, "No id present on both sides → no overlap by modifiedFlows definition")
    }

    @Test
    fun preset_id_change_treated_as_overlapping() {
        // presetId is a scalar — change means both editors took different stances.
        val local = sampleConfig(presetId = "simple-launcher")
        val server = sampleConfig(presetId = "medium-launcher")

        val diff = ConfigDiff.compute(local = local, server = server)
        assertFalse(diff.isEmpty)
        assertTrue(diff.hasOverlappingChanges, "Changed scalar = overlap")
        assertEquals("simple-launcher", diff.presetIdChanged?.local)
        assertEquals("medium-launcher", diff.presetIdChanged?.server)
    }

    @Test
    fun added_contact_only_yields_addedContacts() {
        val c1 = sampleContact("c1111111-1111-4111-8111-111111111111")
        val local = sampleConfig(contacts = emptyList())
        val server = sampleConfig(contacts = listOf(c1))

        val diff = ConfigDiff.compute(local = local, server = server)
        assertEquals(1, diff.addedContacts.size)
        assertEquals(c1.id, diff.addedContacts.first().id)
        assertEquals(0, diff.removedContactIds.size)
        assertEquals(0, diff.modifiedContacts.size)
    }

    @Test
    fun removed_contact_only_yields_removedContactIds() {
        val c1 = sampleContact("c1111111-1111-4111-8111-111111111111")
        val local = sampleConfig(contacts = listOf(c1))
        val server = sampleConfig(contacts = emptyList())

        val diff = ConfigDiff.compute(local = local, server = server)
        assertEquals(0, diff.addedContacts.size)
        assertEquals(1, diff.removedContactIds.size)
        assertEquals(c1.id, diff.removedContactIds.first())
        assertEquals(0, diff.modifiedContacts.size)
    }

    @Test
    fun modified_contact_phone_number_yields_modifiedContacts() {
        val id = ElementId("c1111111-1111-4111-8111-111111111111")
        val localC = Contact(id = id, displayName = "Маша", phoneNumber = "+71111111111")
        val serverC = Contact(id = id, displayName = "Маша", phoneNumber = "+72222222222")
        val local = sampleConfig(contacts = listOf(localC))
        val server = sampleConfig(contacts = listOf(serverC))

        val diff = ConfigDiff.compute(local = local, server = server)
        assertEquals(1, diff.modifiedContacts.size)
        assertEquals("+71111111111", diff.modifiedContacts.first().local.phoneNumber)
        assertEquals("+72222222222", diff.modifiedContacts.first().server.phoneNumber)
        assertTrue(diff.hasOverlappingChanges)
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun sampleConfig(
        presetId: String = "simple-launcher",
        flows: List<Flow> = emptyList(),
        contacts: List<Contact> = emptyList(),
    ): ConfigDocument = ConfigDocument(
        serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
        lastWriterDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
        presetId = presetId,
        flows = flows,
        contacts = contacts,
    )

    private fun sampleFlow(idValue: String): Flow {
        // Allow short test ids; pad to UUID format if needed.
        val id = if (idValue.length == 36) ElementId(idValue) else ElementId("ffffffff-${idValue.padEnd(4, 'f')}-4fff-8fff-ffffffffffff".take(36).let {
            // Build a valid UUID-shape with the test slug embedded.
            "f${idValue.take(7).padEnd(7, '0')}-1111-4111-8111-111111111111"
        })
        return Flow(id = id, title = "test-$idValue", slots = emptyList())
    }

    private fun sampleContact(idValue: String): Contact = Contact(
        id = ElementId(idValue),
        displayName = "Test",
        phoneNumber = "+71234567890",
    )
}
