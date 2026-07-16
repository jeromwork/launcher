package com.launcher.adapters.flow

import com.launcher.api.action.ActionPayload
import com.launcher.preset.fakes.FakeProfileStore
import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Entity
import com.launcher.preset.model.Profile
import com.launcher.preset.model.WizardBehavior
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T127-021 (FR-006, NFR-002, SC-005, SC-009) — the adapter that closes the
 * TASK-52 regression.
 */
class ProfileBackedFlowRepositoryTest {

    private fun entity(
        id: String,
        component: Component,
        parentId: String? = null,
        status: ComponentStatus = ComponentStatus.Applied,
    ) = Entity(
        id = id,
        component = component,
        wizardBehavior = WizardBehavior.AutoApply,
        critical = false,
        status = status,
        parentId = parentId,
    )

    private fun profileOf(vararg entities: Entity) = Profile(
        basedOnPreset = "p",
        presetVersion = 2,
        layoutKey = "grid",
        components = entities.toList(),
    )

    private fun tile(id: String, pkg: String, parentId: String?, status: ComponentStatus = ComponentStatus.Applied) =
        entity(id, Component.AppTile(packageName = pkg, labelKey = "label.$id"), parentId, status)

    private fun hierarchical() = profileOf(
        entity("ws", Component.Workspace()),
        entity("flow-b", Component.Flow(titleKey = "t.b", order = 1), parentId = "ws"),
        entity("flow-a", Component.Flow(titleKey = "t.a", order = 0), parentId = "ws"),
        tile("tile-1", "com.one", parentId = "flow-a"),
        tile("tile-2", "com.two", parentId = "flow-b"),
    )

    // ---- the regression path ----

    @Test
    fun loadFlows_withSavedProfile_returnsImmediately() = runTest {
        val store = FakeProfileStore(profileOf(tile("t", "com.a", parentId = null)))
        val repo = ProfileBackedFlowRepository(store)

        val flows = repo.loadFlows()

        // Post-wizard this must yield tiles, not the empty list that made
        // HomeComponent render Error.
        assertEquals(1, flows.size)
        assertEquals(listOf("t"), flows.single().slots.map { it.id })
    }

    @Test
    fun loadFlows_withNoProfileYet_suspends_lettingCallerTimeoutOwnTheError() = runTest {
        val store = FakeProfileStore(initial = null)
        val repo = ProfileBackedFlowRepository(store)

        val pending = async { repo.loadFlows() }
        testScheduler.advanceUntilIdle()

        // Deliberate: no silent empty list (which would look like "no tiles" and
        // render an empty screen). HomeComponent's existing 3s timeout turns this
        // into Error + Retry.
        assertFalse(pending.isCompleted)
        pending.cancel()
    }

    @Test
    fun loadFlows_completesOnceProfileArrives() = runTest {
        val store = FakeProfileStore(initial = null)
        val repo = ProfileBackedFlowRepository(store)

        val pending = async { repo.loadFlows() }
        testScheduler.advanceUntilIdle()
        store.save(profileOf(tile("t", "com.a", parentId = null)))

        assertEquals(1, pending.await().size)
    }

    // ---- hot path ----

    @Test
    fun observeFlows_nullProfile_doesNotEmit_soHomeStaysLoading() = runTest {
        val store = FakeProfileStore(initial = null)
        val repo = ProfileBackedFlowRepository(store)

        val collected = mutableListOf<List<com.launcher.api.FlowDescriptor>>()
        val job = async { repo.observeFlows().collect { collected += it } }
        testScheduler.advanceUntilIdle()

        assertTrue(collected.isEmpty(), "a transient null must not produce an emission")
        job.cancel()
    }

    @Test
    fun observeFlows_emitsOnEverySave() = runTest {
        val store = FakeProfileStore(profileOf(tile("t1", "com.a", parentId = null)))
        val repo = ProfileBackedFlowRepository(store)

        val collected = mutableListOf<List<com.launcher.api.FlowDescriptor>>()
        val job = async { repo.observeFlows().collect { collected += it } }
        testScheduler.advanceUntilIdle()

        store.save(profileOf(tile("t1", "com.a", parentId = null), tile("t2", "com.b", parentId = null)))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(1, 2), collected.map { it.single().slots.size })
        job.cancel()
    }

    // ---- hierarchical projection (US-4) ----

    @Test
    fun hierarchicalProfile_producesOneDescriptorPerFlow_orderedByFlowOrder() = runTest {
        val repo = ProfileBackedFlowRepository(FakeProfileStore(hierarchical()))

        val flows = repo.loadFlows()

        assertEquals(listOf("flow-a", "flow-b"), flows.map { it.id })
        assertEquals(listOf("t.a", "t.b"), flows.map { it.name })
    }

    @Test
    fun hierarchicalProfile_isolatesSlotsPerFlow() = runTest {
        val repo = ProfileBackedFlowRepository(FakeProfileStore(hierarchical()))

        val flows = repo.loadFlows()

        assertEquals(listOf("tile-1"), flows.first { it.id == "flow-a" }.slots.map { it.id })
        assertEquals(listOf("tile-2"), flows.first { it.id == "flow-b" }.slots.map { it.id })
    }

    @Test
    fun degenerateProfile_withoutFlows_producesSingleSyntheticDescriptor() = runTest {
        val store = FakeProfileStore(
            profileOf(tile("a", "com.a", parentId = null), tile("b", "com.b", parentId = null)),
        )
        val repo = ProfileBackedFlowRepository(store)

        val flows = repo.loadFlows()

        assertEquals(1, flows.size)
        assertEquals(setOf("a", "b"), flows.single().slots.map { it.id }.toSet())
    }

    @Test
    fun emptyProfile_producesNoFlows() = runTest {
        val repo = ProfileBackedFlowRepository(FakeProfileStore(profileOf()))

        assertEquals(emptyList(), repo.loadFlows())
    }

    // ---- render gating ----

    @Test
    fun failedAndSkippedTiles_neverBecomeSlots() = runTest {
        val store = FakeProfileStore(
            profileOf(
                entity("flow", Component.Flow(titleKey = "t")),
                tile("ok", "com.a", parentId = "flow"),
                tile("failed", "com.b", parentId = "flow", status = ComponentStatus.Failed),
                tile("skipped", "com.c", parentId = "flow", status = ComponentStatus.Skipped),
            ),
        )
        val repo = ProfileBackedFlowRepository(store)

        assertEquals(listOf("ok"), repo.loadFlows().single().slots.map { it.id })
    }

    // ---- mapping ----

    @Test
    fun appTile_mapsToOpenAppAction_carryingItsPackage() = runTest {
        val store = FakeProfileStore(profileOf(tile("t", "com.whatsapp", parentId = null)))
        val repo = ProfileBackedFlowRepository(store)

        val slot = repo.loadFlows().single().slots.single()

        val payload = slot.action?.payload as? ActionPayload.OpenApp
        assertEquals("com.whatsapp", payload?.packageHint)
        assertEquals("label.t", slot.label)
    }

    @Test
    fun nonTappableComponent_becomesPlaceholderSlot_notACrash() = runTest {
        val store = FakeProfileStore(
            profileOf(
                entity("flow", Component.Flow(titleKey = "t")),
                entity("sos", Component.Sos(), parentId = "flow"),
            ),
        )
        val repo = ProfileBackedFlowRepository(store)

        assertNull(repo.loadFlows().single().slots.single().action)
    }

    // ---- port parity ----

    @Test
    fun availableTemplates_filtersByPreset() = runTest {
        val repo = ProfileBackedFlowRepository(FakeProfileStore(profileOf()))

        assertEquals(listOf("contacts"), repo.availableTemplates("simple-launcher").map { it.id })
        assertEquals(
            setOf("contacts", "admin_devices"),
            repo.availableTemplates("workspace").map { it.id }.toSet(),
        )
    }

    @Test
    fun addFlow_throws_parityWithConfigBackedImplementation() = runTest {
        val repo = ProfileBackedFlowRepository(FakeProfileStore(profileOf()))

        assertFailsWith<IllegalStateException> { repo.addFlow("contacts") }
    }
}
