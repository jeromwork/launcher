package family.keys

import family.keys.api.PassphraseKdfParams
import family.keys.api.RecoveryVaultBlob
import family.keys.api.SealedConfig
import family.keys.api.WrappedDek
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JSON wire-format roundtrip для F-5 типов (CLAUDE.md rule 5, FR-017, FR-010).
 *
 * Полные backward-compat fixture tests с frozen JSON живут в [SealedConfigBackwardCompatTest]
 * и [RecoveryVaultBackwardCompatTest] (Phase 3/4). Тут — базовая sanity check
 * что serializer-конфигурация работает.
 */
class WireFormatJsonTest {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun sealedConfigRoundtrip() {
        val original = SealedConfig(
            ciphertext = ByteArray(64) { it.toByte() },
            nonce = ByteArray(24) { (it + 100).toByte() },
            aad = ByteArray(32) { (it + 200).toByte() }
        )
        val text = json.encodeToString(original)
        assertContains(text, "\"schemaVersion\":1")
        assertContains(text, "\"algorithm\":\"xchacha20poly1305-v1\"")
        val parsed = json.decodeFromString<SealedConfig>(text)
        assertEquals(original, parsed)
    }

    @Test
    fun recoveryVaultBlobRoundtrip() {
        val original = RecoveryVaultBlob(
            kdfSalt = ByteArray(16) { 0x42 },
            kdfParams = PassphraseKdfParams(),
            wrappedRootKey = ByteArray(48) { it.toByte() },
            nonce = ByteArray(24) { (it + 50).toByte() },
            createdAt = 1_700_000_000L
        )
        val text = json.encodeToString(original)
        assertContains(text, "\"schemaVersion\":1")
        assertContains(text, "\"algorithm\":\"argon2id-xchacha20poly1305-v1\"")
        assertContains(text, "\"memoryKib\":65536")
        assertContains(text, "\"iterations\":3")
        val parsed = json.decodeFromString<RecoveryVaultBlob>(text)
        assertEquals(original, parsed)
    }

    @Test
    fun wrappedDekRoundtrip() {
        val original = WrappedDek(
            name = "config-cipher-aead-v1",
            ciphertext = ByteArray(48) { 0x33 },
            nonce = ByteArray(24) { 0x55 }
        )
        val text = json.encodeToString(original)
        assertContains(text, "\"name\":\"config-cipher-aead-v1\"")
        val parsed = json.decodeFromString<WrappedDek>(text)
        assertEquals(original, parsed)
    }

    @Test
    fun sealedConfigWithNullRecipientSignatureSerializes() {
        val original = SealedConfig(
            ciphertext = ByteArray(16),
            nonce = ByteArray(24),
            aad = ByteArray(8)
        )
        val text = json.encodeToString(original)
        // Null recipientMasterSignature должно быть либо "null", либо absent. Оба ок.
        val parsed = json.decodeFromString<SealedConfig>(text)
        assertEquals(null, parsed.recipientMasterSignature)
    }

    @Test
    fun rootKeyToStringDoesNotLeakBytes() {
        val rk = family.keys.api.RootKey(ByteArray(32) { 0xAB.toByte() })
        val str = rk.toString()
        assertTrue("***" in str, "RootKey.toString must mask bytes")
        assertTrue("ab" !in str.lowercase(), "RootKey.toString must NOT leak hex bytes")
    }
}
