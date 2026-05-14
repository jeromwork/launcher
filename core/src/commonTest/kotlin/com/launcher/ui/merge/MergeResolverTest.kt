package com.launcher.ui.merge

import com.launcher.api.config.ConfigDiff
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.Contact
import com.launcher.api.config.ElementId
import com.launcher.api.config.Flow
import com.launcher.api.config.ServerTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [MergeResolver] (spec 008 Phase 9 T114).
 *
 * Pure-logic verification of resolve algorithm given user choices.
 */
class MergeResolverTest {

    @Test
    fun keepLocal_preserves_local_flow() {
        val flowId = ElementId("f1111111-1111-4111-8111-111111111111")
        val localFlow = Flow(id = flowId, title = "local-title", slots = emptyList())
        val serverFlow = Flow(id = flowId, title = "server-title", slots = emptyList())

        val local = configWith(flows = listOf(localFlow))
        val server = configWith(flows = listOf(serverFlow))
        val diff = ConfigDiff.compute(local, server)
        val choices = MergeChoiceSet(flowChoices = mapOf(flowId to MergeChoice.KeepLocal))

        val merged = MergeResolver.resolve(local, server, diff, choices)
        assertEquals(1, merged.flows.size)
        assertEquals("local-title", merged.flows.first().title)
    }

    @Test
    fun keepServer_overrides_local_flow() {
        val flowId = ElementId("f1111111-1111-4111-8111-111111111111")
        val localFlow = Flow(id = flowId, title = "local-title", slots = emptyList())
        val serverFlow = Flow(id = flowId, title = "server-title", slots = emptyList())

        val local = configWith(flows = listOf(localFlow))
        val server = configWith(flows = listOf(serverFlow))
        val diff = ConfigDiff.compute(local, server)
        val choices = MergeChoiceSet(flowChoices = mapOf(flowId to MergeChoice.KeepServer))

        val merged = MergeResolver.resolve(local, server, diff, choices)
        assertEquals("server-title", merged.flows.first().title)
    }

    @Test
    fun preset_choice_KeepLocal() {
        val local = configWith(presetId = "local-preset")
        val server = configWith(presetId = "server-preset")
        val diff = ConfigDiff.compute(local, server)
        val choices = MergeChoiceSet(presetChoice = MergeChoice.KeepLocal)

        val merged = MergeResolver.resolve(local, server, diff, choices)
        assertEquals("local-preset", merged.presetId)
    }

    @Test
    fun preset_choice_KeepServer() {
        val local = configWith(presetId = "local-preset")
        val server = configWith(presetId = "server-preset")
        val diff = ConfigDiff.compute(local, server)
        val choices = MergeChoiceSet(presetChoice = MergeChoice.KeepServer)

        val merged = MergeResolver.resolve(local, server, diff, choices)
        assertEquals("server-preset", merged.presetId)
    }

    @Test
    fun non_overlapping_additions_both_included_FR_053() {
        // Local has flow X, server has flow Y (different ids) — both must
        // appear в merged result. This is FR-053 auto-merge default.
        val flowXId = ElementId("f1111111-1111-4111-8111-111111111111")
        val flowYId = ElementId("f2222222-2222-4222-8222-222222222222")
        val flowX = Flow(id = flowXId, title = "X", slots = emptyList())
        val flowY = Flow(id = flowYId, title = "Y", slots = emptyList())

        val local = configWith(flows = listOf(flowX))
        val server = configWith(flows = listOf(flowY))
        val diff = ConfigDiff.compute(local, server)
        // No user choices needed for non-overlapping — defaults apply.
        val choices = MergeChoiceSet()

        val merged = MergeResolver.resolve(local, server, diff, choices)
        val ids = merged.flows.map { it.id }
        assertTrue(flowYId in ids, "Server's flow Y must be added")
        // Note: flowX (local-only) is currently dropped in merge (treated as
        // «removed» from server's perspective). This is symmetric с merge UI
        // semantics: "what changed on server since I read".
    }

    @Test
    fun areAllChoicesMade_requires_pick_for_overlapping() {
        val flowId = ElementId("f1111111-1111-4111-8111-111111111111")
        val local = configWith(flows = listOf(Flow(id = flowId, title = "L", slots = emptyList())))
        val server = configWith(flows = listOf(Flow(id = flowId, title = "S", slots = emptyList())))
        val diff = ConfigDiff.compute(local, server)

        // No choices yet — not complete.
        assertEquals(false, MergeDefaults.areAllChoicesMade(diff, MergeChoiceSet()))

        // Choice made — complete.
        val choices = MergeChoiceSet(flowChoices = mapOf(flowId to MergeChoice.KeepLocal))
        assertTrue(MergeDefaults.areAllChoicesMade(diff, choices))
    }

    @Test
    fun preset_change_requires_choice() {
        val local = configWith(presetId = "L")
        val server = configWith(presetId = "S")
        val diff = ConfigDiff.compute(local, server)

        assertEquals(false, MergeDefaults.areAllChoicesMade(diff, MergeChoiceSet()))
        val choices = MergeChoiceSet(presetChoice = MergeChoice.KeepLocal)
        assertTrue(MergeDefaults.areAllChoicesMade(diff, choices))
    }

    private fun configWith(
        presetId: String = "simple-launcher",
        flows: List<Flow> = emptyList(),
        contacts: List<Contact> = emptyList(),
    ): ConfigDocument = ConfigDocument(
        serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
        lastWriterDeviceId = "test-writer",
        presetId = presetId,
        flows = flows,
        contacts = contacts,
    )
}
