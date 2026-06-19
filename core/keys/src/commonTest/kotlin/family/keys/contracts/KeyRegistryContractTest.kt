package family.keys.contracts

import family.keys.api.KeyRegistryError
import family.keys.api.Outcome
import family.keys.fakes.FakeKeyRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract test для [family.keys.api.KeyRegistry] (T028, FR-004).
 *
 * Любой adapter этого port'а должен пройти этот contract.
 */
class KeyRegistryContractTest {

    @Test
    fun registerAndGetRoundtrip() = runTest {
        val registry = FakeKeyRegistry()
        val dek = ByteArray(32) { it.toByte() }

        val register = registry.registerDek("config-cipher-aead-v1", dek)
        assertIs<Outcome.Success<Unit>>(register)

        assertTrue(registry.hasDek("config-cipher-aead-v1"))

        val get = registry.getDek("config-cipher-aead-v1")
        assertIs<Outcome.Success<ByteArray>>(get)
        assertContentEquals(dek, get.value)
    }

    @Test
    fun unknownDekReturnsNotFound() = runTest {
        val registry = FakeKeyRegistry()

        val get = registry.getDek("unknown-dek-name")
        assertIs<Outcome.Failure<KeyRegistryError>>(get)
        assertEquals(KeyRegistryError.NotFound, get.error)
        assertFalse(registry.hasDek("unknown-dek-name"))
    }

    @Test
    fun reregisteringSameNameOverwrites() = runTest {
        val registry = FakeKeyRegistry()
        val v1 = ByteArray(32) { 0x11 }
        val v2 = ByteArray(32) { 0x22 }

        registry.registerDek("rotated-dek", v1)
        registry.registerDek("rotated-dek", v2)

        val get = registry.getDek("rotated-dek")
        assertIs<Outcome.Success<ByteArray>>(get)
        assertContentEquals(v2, get.value)
    }

    @Test
    fun getDekReturnsDefensiveCopy() = runTest {
        val registry = FakeKeyRegistry()
        val dek = ByteArray(32) { 0x55 }
        registry.registerDek("dek1", dek)

        val first = registry.getDek("dek1") as Outcome.Success<ByteArray>
        first.value.fill(0xFF.toByte())

        val second = registry.getDek("dek1") as Outcome.Success<ByteArray>
        assertContentEquals(dek, second.value, "Mutation of returned bytes MUST NOT affect stored DEK")
    }
}
