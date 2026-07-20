package family.pairing.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TASK-51 T064 — wire-format roundtrip + backward-compat read for
 * [EncryptedEnvelope] (CLAUDE.md §5, contracts/encrypted-envelope.md, FR-004).
 *
 * Same pattern as [DeviceIdentitySerializationTest]: roundtrip parity and
 * @SerialName stability across the family→cryptokit rename.
 */
class EncryptedEnvelopeSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun roundtrip_serialize_then_deserialize_returns_equal_value() {
        val original = sampleEnvelope()
        val text = json.encodeToString(EncryptedEnvelope.serializer(), original)
        val decoded = json.decodeFromString(EncryptedEnvelope.serializer(), text)
        assertEquals(original, decoded, "EncryptedEnvelope must roundtrip equal")
    }

    @Test
    fun backwardCompat_canonical_json_still_deserializes() {
        val sample = sampleEnvelope()
        val canonical = json.encodeToString(EncryptedEnvelope.serializer(), sample)
        assertTrue(canonical.contains("\"schemaVersion\""))
        assertTrue(canonical.contains("\"cipherSuiteId\""))
        assertTrue(canonical.contains("\"recipients\""))
        assertTrue(canonical.contains("\"nonce\""))
        assertTrue(canonical.contains("\"ciphertext\""))
        assertTrue(canonical.contains("\"mac\""))
        val decoded = json.decodeFromString(EncryptedEnvelope.serializer(), canonical)
        assertEquals(sample, decoded)
    }

    private fun sampleEnvelope(): EncryptedEnvelope {
        val recipient = Recipient(
            deviceId = DeviceId("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
            sealedCEK = ByteArray(SEALED_CEK_SIZE) { (it + 7).toByte() },
        )
        return EncryptedEnvelope(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            cipherSuiteId = CIPHER_SUITE_ID_V1,
            nonce = ByteArray(XCHACHA20_NONCE_SIZE) { (it + 11).toByte() },
            recipients = listOf(recipient),
            ciphertext = ByteArray(64) { (it + 13).toByte() },
            mac = ByteArray(POLY1305_MAC_SIZE) { (it + 17).toByte() },
            metadata = mapOf("kind" to "test".encodeToByteArray()),
        )
    }
}
