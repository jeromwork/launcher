package cryptokit.keys.contracts

import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecoveryKeyBackupBlob
import cryptokit.keys.api.KdfParams
import cryptokit.keys.impl.RecoveryBlobCodec
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract test: RecoveryKeyBackupBlob roundtrip (T622, SC-013, FR-023).
 *
 * encode → decode → assert structural equality (все поля включая kdfParams).
 * Catches kotlinx-serialization drift, field ordering, base64 round-trips.
 */
class RecoveryKeyBackupBlobRoundtripTest {

    private fun sampleBlob(seed: Byte = 0x42) = RecoveryKeyBackupBlob(
        stableId = "00000000-0000-4000-8000-000000000001",
        salt = ByteArray(32) { seed },
        kdfParams = KdfParams(),
        ciphertext = ByteArray(48) { (seed + it).toByte() },
        nonce = ByteArray(24) { (seed - it).toByte() },
        createdAt = Instant.parse("2026-06-28T10:00:00Z")
    )

    @Test
    fun encodeDecodeSurvivesRoundtrip() {
        val original = sampleBlob()
        val json = RecoveryBlobCodec.encode(original)
        val result = RecoveryBlobCodec.decode(json)

        assertIs<Outcome.Success<RecoveryKeyBackupBlob>>(result, "Roundtrip decode MUST succeed")
        assertEquals(original, result.value, "Decoded blob MUST be structurally equal to original")
    }

    @Test
    fun roundtripPreservesKdfParams() {
        val original = sampleBlob()
        val json = RecoveryBlobCodec.encode(original)
        val decoded = (RecoveryBlobCodec.decode(json) as Outcome.Success).value

        assertEquals(original.kdfParams, decoded.kdfParams, "kdfParams MUST round-trip exactly")
    }

    @Test
    fun roundtripPreservesByteArrayFields() {
        val original = sampleBlob(0x77)
        val json = RecoveryBlobCodec.encode(original)
        val decoded = (RecoveryBlobCodec.decode(json) as Outcome.Success).value

        assertTrue(
            original.salt.contentEquals(decoded.salt),
            "salt MUST round-trip byte-exact"
        )
        assertTrue(
            original.ciphertext.contentEquals(decoded.ciphertext),
            "ciphertext MUST round-trip byte-exact"
        )
        assertTrue(
            original.nonce.contentEquals(decoded.nonce),
            "nonce MUST round-trip byte-exact"
        )
    }

    @Test
    fun roundtripPreservesSchemaVersion() {
        val blob = sampleBlob()
        val json = RecoveryBlobCodec.encode(blob)
        val decoded = (RecoveryBlobCodec.decode(json) as Outcome.Success).value
        assertEquals(1, decoded.schemaVersion)
    }

    @Test
    fun jsonContainsSchemaVersionField() {
        val blob = sampleBlob()
        val json = RecoveryBlobCodec.encode(blob)
        assertTrue(
            json.contains("\"schemaVersion\""),
            "JSON MUST contain schemaVersion field (contracts/recovery-key-backup-v1.md §1)"
        )
    }
}
