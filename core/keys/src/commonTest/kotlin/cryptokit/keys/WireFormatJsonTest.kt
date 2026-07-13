package cryptokit.keys

import cryptokit.keys.api.Envelope
import cryptokit.keys.api.KdfParams
import cryptokit.keys.api.RecoveryKeyBackupBlob
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JSON wire-format roundtrip sanity check for the surviving `:core:keys`
 * wire types (CLAUDE.md rule 5).
 *
 * Full backward-compat fixture tests for [RecoveryKeyBackupBlob] live in
 * [RecoveryKeyBackupBlobContractBackwardCompatTest]. The envelope wire format
 * (Envelope) is covered by [EnvelopeConfigCipherRoundtripTest] +
 * [EnvelopeRemoteStorageTest].
 */
class WireFormatJsonTest {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun envelopeRoundtrip() {
        val original = Envelope(
            ciphertext = ByteArray(64) { it.toByte() },
            nonce = ByteArray(24) { (it + 100).toByte() },
            aad = ByteArray(32) { (it + 200).toByte() },
            recipientKeys = mapOf(
                "phone-abc" to ByteArray(80) { it.toByte() },
                "tablet-def" to ByteArray(80) { (it + 1).toByte() }
            )
        )
        val text = json.encodeToString(original)
        assertContains(text, "\"schemaVersion\":1")
        assertContains(text, "\"algorithm\":\"envelope-xchacha20poly1305-x25519-v1\"")
        assertContains(text, "\"phone-abc\"")
        assertContains(text, "\"tablet-def\"")
        val parsed = json.decodeFromString<Envelope>(text)
        assertEquals(original, parsed)
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
        val rk = cryptokit.keys.api.RootKey(ByteArray(32) { 0xAB.toByte() })
        val str = rk.toString()
        assertTrue("***" in str, "RootKey.toString must mask bytes")
        assertTrue("ab" !in str.lowercase(), "RootKey.toString must NOT leak hex bytes")
    }
}
