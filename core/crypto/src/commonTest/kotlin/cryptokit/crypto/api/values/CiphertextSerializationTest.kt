package cryptokit.crypto.api.values

import cryptokit.crypto.exception.CryptoException
import cryptokit.crypto.fake.FakeAeadCipher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * TASK-51 T065 — wire-format tests for [Ciphertext] (CLAUDE.md §5, contracts/ciphertext.md,
 * FR-004, SC-013).
 *
 * Wire layout: `nonce(24) || ciphertext || mac(16)` — minimum 40 bytes.
 *
 *  1. **Init validator** — `Ciphertext` itself is a `@JvmInline value class` without an
 *     `init` block (rejection of short blobs lives in [cryptokit.crypto.api.AeadCipher.decrypt]).
 *     We verify the rejection contract via the decrypt path: `AeadCipher.decrypt` MUST throw
 *     [CryptoException.MalformedCiphertext] on a sub-40-byte payload.
 *  2. **Roundtrip** — encrypt → decrypt produces byte-equal plaintext.
 *  3. **Backward-compat** — caller can construct `Ciphertext(bytes)` from a hardcoded
 *     fixture of correct shape and the AEAD decrypt path remains structurally addressable
 *     (rejects MAC mismatch with [CryptoException.DecryptionFailed], not crash).
 */
class CiphertextSerializationTest {

    private val cipher = FakeAeadCipher()
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val aad = "test-aad".encodeToByteArray()

    @Test
    fun initValidator_rejectsTooShortPayloadOnDecrypt() = runTest {
        val tooShort = Ciphertext(ByteArray(20)) // < 24 + 16 minimum
        assertFailsWith<CryptoException.MalformedCiphertext> {
            cipher.decrypt(tooShort, key, aad)
        }
    }

    @Test
    fun roundtrip_encryptThenDecrypt_returnsByteEqualPlaintext() = runTest {
        val plaintext = "the quick brown fox jumps over the lazy dog".encodeToByteArray()
        val ct: Ciphertext = cipher.encrypt(plaintext, key, aad)

        assertTrue(
            ct.bytes.size >= 24 + 16,
            "Ciphertext envelope must include nonce(24) + mac(16) + body — got ${ct.bytes.size}",
        )

        val recovered = cipher.decrypt(ct, key, aad)
        assertEquals(plaintext.toList(), recovered.toList(), "decrypt must recover original bytes")
    }

    @Test
    fun backwardCompat_fixedShapeBytes_constructible_andDecryptRejectsMacMismatch() = runTest {
        // Hardcoded golden bytes: 24-byte nonce || 24-byte ciphertext body || 16-byte mac = 64 total.
        // These bytes do NOT correspond to a valid encryption under `key` — verifying that
        // the structural shape is accepted (no init throw, no parser crash) AND that the MAC
        // verification path rejects cleanly via DecryptionFailed (not silent or NPE).
        val nonce = ByteArray(24) { (0x10 + it).toByte() }
        val body = ByteArray(24) { (0x40 + it).toByte() }
        val mac = ByteArray(16) { 0xAA.toByte() }
        val wire = nonce + body + mac
        assertEquals(64, wire.size)

        // Construction MUST succeed (no init throw — Ciphertext is a thin value class).
        val ct = Ciphertext(wire)
        assertEquals(64, ct.bytes.size)

        // Decrypt MUST throw DecryptionFailed (MAC mismatch), not crash with another error.
        assertFailsWith<CryptoException.DecryptionFailed> {
            cipher.decrypt(ct, key, aad)
        }
    }
}
