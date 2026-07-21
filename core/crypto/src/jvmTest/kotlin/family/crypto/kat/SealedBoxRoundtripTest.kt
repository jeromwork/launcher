package family.crypto.kat

import family.crypto.libsodium.LibsodiumAsymmetricCrypto
import family.crypto.libsodium.LibsodiumRandomSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sealed-box + LibsodiumRandomSource integration smoke tests. Per FR-007 (sealForRecipient/
 * openSealed) — these primitives are used by ADR-008 social recovery (future spec, TBD envelopes).
 */
class SealedBoxRoundtripTest {

    private val crypto = LibsodiumAsymmetricCrypto()
    private val random = LibsodiumRandomSource()

    @Test
    fun sealOpenRoundtrip_recoverPayload() = runTest {
        val recipient = crypto.generateX25519KeyPair()
        val payload = "the cek for ADR-008 social recovery".encodeToByteArray()
        val sealed = crypto.sealForRecipient(payload, recipient.publicKey)
        // sealed-box adds 48-byte overhead (32 ephemeralPub + 16 mac).
        assertEquals(payload.size + 48, sealed.bytes.size)
        val opened = crypto.openSealed(sealed, recipient.privateKey)
        assertContentEquals(payload, opened)
    }

    @Test
    fun sealOpen_wrongRecipient_fails() = runTest {
        val recipient = crypto.generateX25519KeyPair()
        val impostor = crypto.generateX25519KeyPair()
        val sealed = crypto.sealForRecipient(byteArrayOf(1, 2, 3), recipient.publicKey)
        val result = runCatching { crypto.openSealed(sealed, impostor.privateKey) }
        assertEquals(true, result.isFailure)
    }

    @Test
    fun randomSource_returnsRequestedLengthAndDiffers() = runTest {
        val a = random.nextBytes(32)
        val b = random.nextBytes(32)
        assertEquals(32, a.size)
        assertEquals(32, b.size)
        assertTrue(!a.contentEquals(b), "two CSPRNG draws must differ")
    }
}
