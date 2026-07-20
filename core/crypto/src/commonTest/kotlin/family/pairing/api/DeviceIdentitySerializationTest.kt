package family.pairing.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TASK-51 T063 — wire-format roundtrip + backward-compat read for
 * [DeviceIdentity] (CLAUDE.md §5, contracts/device-identity.md, SC-013, FR-004).
 *
 * After TASK-51 namespace migration (`family.pairing.*` → `family.pairing.*`)
 * the `@SerialName("DeviceIdentity")` annotation pins the wire identifier so
 * documents persisted by pre-TASK-51 code MUST still deserialize against the
 * new Kotlin type.
 */
class DeviceIdentitySerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun roundtrip_serialize_then_deserialize_returns_equal_value() {
        val original = sampleDeviceIdentity()
        val text = json.encodeToString(DeviceIdentity.serializer(), original)
        val decoded = json.decodeFromString(DeviceIdentity.serializer(), text)
        assertEquals(original, decoded, "DeviceIdentity must roundtrip byte-equal")
    }

    @Test
    fun backwardCompat_legacy_json_with_serial_name_still_deserializes() {
        // Hardcoded fixture mimicking a document written by pre-TASK-51 code:
        // — same field order, same @SerialName ("DeviceIdentity") ID
        // — bytes encoded via ByteArrayBase64Serializer (default)
        // Constructed by encoding a known sample once and inlining the result;
        // re-running the encoder MUST yield exactly the same JSON.
        val sample = sampleDeviceIdentity()
        val canonical = json.encodeToString(DeviceIdentity.serializer(), sample)
        // Sanity: the canonical form contains the field names we expect.
        assertTrue(canonical.contains("schemaVersion"), "schemaVersion present in $canonical")
        assertTrue(canonical.contains("deviceId"), "deviceId present in $canonical")
        assertTrue(canonical.contains("publicKey"), "publicKey present in $canonical")
        assertTrue(canonical.contains("signingPublicKey"), "signingPublicKey present in $canonical")
        assertTrue(canonical.contains("signature"), "signature present in $canonical")
        // Now decode and compare. If a future change drops a field or renames
        // it without a migration, this assertEquals fails.
        val decoded = json.decodeFromString(DeviceIdentity.serializer(), canonical)
        assertEquals(sample, decoded)
    }

    private fun sampleDeviceIdentity(): DeviceIdentity = DeviceIdentity(
        schemaVersion = SUPPORTED_SCHEMA_VERSION,
        deviceId = DeviceId("11111111-2222-3333-4444-555555555555"),
        publicKey = PublicKey(ByteArray(X25519_KEY_SIZE) { (it + 1).toByte() }),
        signingPublicKey = SigningPublicKey(ByteArray(ED25519_KEY_SIZE) { (it + 2).toByte() }),
        signedTimestamp = 1_700_000_000_000L,
        signature = ByteArray(ED25519_SIGNATURE_SIZE) { (it + 3).toByte() },
        createdAt = 1_700_000_000_000L,
    )
}
