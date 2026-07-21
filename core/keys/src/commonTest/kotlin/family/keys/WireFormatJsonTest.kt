package family.keys

import family.keys.api.KdfParams
import family.keys.api.RecoveryKeyBackupBlob
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JSON wire-format roundtrip sanity check for the surviving `:core:keys`
 * @Serializable wire types (CLAUDE.md rule 5).
 *
 * [Envelope] is no longer @Serializable (TASK-141 — it is a pure crypto type);
 * its wire lives in the storage adapter (`FirestoreEnvelopeStorage`) and is
 * covered by `FirestoreEnvelopeWireFormatTest` + [EnvelopeConfigCipherRoundtripTest]
 * (crypto roundtrip) + [EnvelopeBackwardCompatTest] (frozen-ciphertext decrypt).
 * Full backward-compat fixture tests for [RecoveryKeyBackupBlob] live in
 * [RecoveryKeyBackupBlobContractBackwardCompatTest].
 */
class WireFormatJsonTest {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun recoveryKeyBackupBlobRoundtrip() {
        val original = RecoveryKeyBackupBlob(
            stableId = "00000000-0000-4000-8000-000000000001",
            salt = ByteArray(32) { 0x42 },
            kdfParams = KdfParams(),
            ciphertext = ByteArray(48) { it.toByte() },
            nonce = ByteArray(24) { (it + 50).toByte() },
            createdAt = Instant.parse("2026-06-28T10:00:00Z")
        )
        val text = json.encodeToString(original)
        assertContains(text, "\"schemaVersion\":1")
        assertContains(text, "\"stableId\":\"00000000-0000-4000-8000-000000000001\"")
        assertContains(text, "\"memoryKb\":65536")
        assertContains(text, "\"iterations\":3")
        val parsed = json.decodeFromString<RecoveryKeyBackupBlob>(text)
        assertEquals(original, parsed)
    }

    @Test
    fun rootKeyToStringDoesNotLeakBytes() {
        val rk = family.keys.api.RootKey(ByteArray(32) { 0xAB.toByte() })
        val str = rk.toString()
        assertTrue("***" in str, "RootKey.toString must mask bytes")
        assertTrue("ab" !in str.lowercase(), "RootKey.toString must NOT leak hex bytes")
    }
}
