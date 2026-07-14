package cryptokit.keys.vault

import cryptokit.keys.api.vault.Purpose
import cryptokit.keys.api.vault.TestRecoveryStrategy
import cryptokit.keys.api.vault.VaultException
import cryptokit.keys.api.vault.canonicalAad
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * SC-008 dedicated coverage — the header `purpose_id` field is the second-line defence: even if
 * two callers accidentally share the same derived key, opening a CONFIG blob under
 * RECOVERY_BLOB fails at the header check before the AEAD MAC is evaluated.
 */
class PurposeEnforcementTest {

    private fun aad() = canonicalAad("ns", 1, 1)

    @Test
    fun `open CONFIG blob under RECOVERY_BLOB throws WrongPurpose`() = runTest {
        val vault = FakeKeyVault()
        vault.unlock(TestRecoveryStrategy())
        val ct = vault.aeadSeal(Purpose.CONFIG, "payload".encodeToByteArray(), aad())
        val ex = assertFailsWith<VaultException.WrongPurpose> {
            vault.aeadOpen(Purpose.RECOVERY_BLOB, ct, aad())
        }
        assertEquals(Purpose.CONFIG.stableId, ex.actualStableId)
        assertEquals(Purpose.RECOVERY_BLOB, ex.expected)
    }

    @Test
    fun `Purpose stableId is stable across enum reorder — CONFIG=0x0001 RECOVERY_BLOB=0x0002`() {
        assertEquals(0x0001, Purpose.CONFIG.stableId)
        assertEquals(0x0002, Purpose.RECOVERY_BLOB.stableId)
    }

    @Test
    fun `Purpose fromStableId lookup`() {
        assertEquals(Purpose.CONFIG, Purpose.fromStableId(0x0001))
        assertEquals(Purpose.RECOVERY_BLOB, Purpose.fromStableId(0x0002))
        assertEquals(null, Purpose.fromStableId(0xFFFF))
    }
}
