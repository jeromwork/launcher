package family.keys.contracts

import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.api.PassphraseKdfParams
import family.keys.impl.RecoveryBlobCodec
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
        kdfSalt = ByteArray(16) { seed },
        kdfParams = PassphraseKdfParams(),
        wrappedRootKey = ByteArray(48) { (seed + it).toByte() },
        nonce = ByteArray(24) { (seed - it).toByte() },
        createdAt = 1_700_000_000L
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
            original.kdfSalt.contentEquals(decoded.kdfSalt),
            "kdfSalt MUST round-trip byte-exact"
        )
        assertTrue(
            original.wrappedRootKey.contentEquals(decoded.wrappedRootKey),
            "wrappedRootKey MUST round-trip byte-exact"
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
