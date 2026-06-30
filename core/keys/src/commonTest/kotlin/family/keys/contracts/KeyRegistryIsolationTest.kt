package family.keys.contracts

import family.keys.api.Outcome
import family.keys.fakes.FakeKeyRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract test: KeyRegistry namespace isolation (T621, SC-013, FR-023).
 *
 * Разные stableId → разные DerivedKey для одинакового purpose;
 * wipe одного namespace не затрагивает другие (SC-012).
 */
class KeyRegistryIsolationTest {

    @Test
    fun differentStableIdsDifferentKeys() = runTest {
        val registry = FakeKeyRegistry()
        val purpose = "config"

        val aliceId = "00000000-0000-4000-8000-000000000001"
        val bobId = "00000000-0000-4000-8000-000000000002"

        val aliceKey = (registry.derive(aliceId, purpose) as Outcome.Success).value
        val bobKey = (registry.derive(bobId, purpose) as Outcome.Success).value

        assertTrue(
            !aliceKey.bytes.contentEquals(bobKey.bytes),
            "Different stableIds MUST derive different keys for same purpose (FR-031)"
        )
    }

    @Test
    fun wipeOneNamespaceDoesNotAffectOthers() = runTest {
        val registry = FakeKeyRegistry()
        val purpose = "config"

        val aliceId = "00000000-0000-4000-8000-000000000001"
        val bobId = "00000000-0000-4000-8000-000000000002"

        // Derive for both.
        registry.derive(aliceId, purpose)
        registry.derive(bobId, purpose)

        // Wipe alice namespace.
        val wipeResult = registry.wipeAll(aliceId)
        assertIs<Outcome.Success<Unit>>(wipeResult)

        // Alice namespace empty.
        val aliceList = (registry.list(aliceId) as Outcome.Success).value
        assertTrue(aliceList.isEmpty(), "After wipeAll, list MUST be empty (SC-012)")

        // Bob namespace untouched.
        val bobList = (registry.list(bobId) as Outcome.Success).value
        assertTrue(bobList.isNotEmpty(), "Wipe of alice MUST NOT affect bob namespace (FR-031)")
        assertTrue(
            bobList.contains(purpose),
            "Bob's config key MUST still be present after alice wipe"
        )
    }

    @Test
    fun listReturnsAllPurposesForNamespace() = runTest {
        val registry = FakeKeyRegistry()
        val stableId = "00000000-0000-4000-8000-000000000001"

        registry.derive(stableId, "config")
        registry.derive(stableId, "contacts")
        registry.derive(stableId, "media")

        val list = (registry.list(stableId) as Outcome.Success).value
        assertTrue(list.containsAll(listOf("config", "contacts", "media")))
    }

    @Test
    fun wipeAllThenListEmpty() = runTest {
        val registry = FakeKeyRegistry()
        val stableId = "00000000-0000-4000-8000-000000000001"

        registry.derive(stableId, "config")
        registry.wipeAll(stableId)

        val list = (registry.list(stableId) as Outcome.Success).value
        assertTrue(list.isEmpty(), "list() MUST return empty after wipeAll (SC-012)")
    }
}
