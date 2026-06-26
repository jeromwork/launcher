package cryptokit.crypto.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [CryptoException] declares (at minimum) the five broad subclasses
 * required by TASK-51 data-model §1 (FR-018). Granular spec-016 subclasses may
 * additionally co-exist; they are removed in a later refactor.
 */
class CryptoExceptionHierarchyTest {

    @Test
    fun cryptoExceptionIsSealed() {
        assertTrue(CryptoException::class.isSealed, "CryptoException must be sealed")
    }

    @Test
    fun cryptoExceptionDeclaresFiveBroadSubclasses() {
        val names = CryptoException::class.sealedSubclasses.map { it.simpleName }.toSet()
        val required = setOf(
            "AeadException",
            "KeyStoreException",
            "KeyDerivationException",
            "NativeLinkException",
            "SerializationException",
        )
        val missing = required - names
        assertEquals(emptySet(), missing, "missing required broad subclasses: $missing")
    }
}
