package cryptokit.pairing.api

import cryptokit.crypto.fake.FakeAeadCipher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TASK-51 T062 — rewrite of the legacy `CryptoEnvelopeWireFormatTest`
 * (previously under `com/launcher/api/crypto/`, deleted in Phase 7).
 *
 * After the family→cryptokit rename the test lives in
 * `core/crypto/src/commonTest/kotlin/cryptokit/pairing/api/` and is driven by
 * the cryptokit fakes (FakeAeadCipher). Verifies:
 *  • AEAD encrypt → decrypt roundtrip yields byte-equal plaintext (FR-007).
 *  • Construction of an EncryptedEnvelope with the correct wire layout passes
 *    its init validators (nonce 24, mac 16, recipients ≥ 1).
 */
class CryptoEnvelopeWireFormatTest {

    @Test
    fun aead_encrypt_decrypt_byteEqualRoundtrip() = runTest {
        val cipher = FakeAeadCipher()
        val key = ByteArray(32) { (it * 3 + 1).toByte() }
        val aad = "envelope-aad".encodeToByteArray()
        val plaintext = "spec-007 pairing payload, лорем ипсум".encodeToByteArray()

        val ct = cipher.encrypt(plaintext, key, aad)
        val recovered = cipher.decrypt(ct, key, aad)

        assertEquals(plaintext.toList(), recovered.toList())
    }

    @Test
    fun envelope_validShape_initValidatorsAccept() {
        val recipient = Recipient(
            deviceId = DeviceId("12345678-1234-1234-1234-1234567890ab"),
            sealedCEK = ByteArray(SEALED_CEK_SIZE) { it.toByte() },
        )
        val env = EncryptedEnvelope(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            cipherSuiteId = CIPHER_SUITE_ID_V1,
            nonce = ByteArray(XCHACHA20_NONCE_SIZE) { (0x20 + it).toByte() },
            recipients = listOf(recipient),
            ciphertext = ByteArray(32) { (0x40 + it).toByte() },
            mac = ByteArray(POLY1305_MAC_SIZE) { (0x60 + it).toByte() },
        )
        assertEquals(SUPPORTED_SCHEMA_VERSION, env.schemaVersion)
        assertEquals(CIPHER_SUITE_ID_V1, env.cipherSuiteId)
        assertTrue(env.recipients.isNotEmpty())
        assertEquals(XCHACHA20_NONCE_SIZE, env.nonce.size)
        assertEquals(POLY1305_MAC_SIZE, env.mac.size)
    }
}
