package family.keys.contracts

import family.keys.api.DerivedKey
import family.keys.api.Outcome
import family.keys.fakes.FakeKeyRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract test: KeyRegistry derivation determinism (T620, SC-013, FR-023, plan §Test Strategy).
 *
 * Те же stableId + purpose → тот же DerivedKey (10 итераций);
 * разные purpose → разные ключи.
 */
class KeyRegistryDerivationDeterminismTest {

    @Test
    fun samePurposeSameKeyTenIterations() = runTest {
        val registry = FakeKeyRegistry()
        val stableId = "00000000-0000-4000-8000-000000000001"
        val purpose = "config"

        // Derive 10 times — all must produce the same bytes.
        val results = (1..10).map {
            val outcome = registry.derive(stableId, purpose)
            assertIs<Outcome.Success<DerivedKey>>(outcome)
            outcome.value.bytes.copyOf()
        }

        val first = results.first()
        results.forEach { bytes ->
            assertTrue(
                first.contentEquals(bytes),
                "Same stableId+purpose MUST always derive the same key (FR-023 determinism)"
            )
        }
    }

    @Test
    fun differentPurposesDifferentKeys() = runTest {
        val registry = FakeKeyRegistry()
        val stableId = "00000000-0000-4000-8000-000000000001"

        val configOutcome = registry.derive(stableId, "config")
        val contactsOutcome = registry.derive(stableId, "contacts")
        val mediaOutcome = registry.derive(stableId, "media")

        assertIs<Outcome.Success<DerivedKey>>(configOutcome)
        assertIs<Outcome.Success<DerivedKey>>(contactsOutcome)
        assertIs<Outcome.Success<DerivedKey>>(mediaOutcome)

        val configKey = configOutcome.value
        val contactsKey = contactsOutcome.value
        val mediaKey = mediaOutcome.value

        assertTrue(
            !configKey.bytes.contentEquals(contactsKey.bytes),
            "Different purposes MUST produce different derived keys"
        )
        assertTrue(
            !configKey.bytes.contentEquals(mediaKey.bytes),
            "Different purposes MUST produce different derived keys"
        )
        assertTrue(
            !contactsKey.bytes.contentEquals(mediaKey.bytes),
            "Different purposes MUST produce different derived keys"
        )
    }

    @Test
    fun derivedKeyIs32Bytes() = runTest {
        val registry = FakeKeyRegistry()
        val outcome = registry.derive("some-stable-id", "config")
        assertIs<Outcome.Success<DerivedKey>>(outcome)
        assertEquals(32, outcome.value.bytes.size, "DerivedKey MUST be 32 bytes")
    }
}
