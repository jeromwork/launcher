package family.keys

import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.AuthIdentity
import family.keys.api.CipherError
import family.keys.api.Outcome
import family.keys.api.SealedConfig
import family.keys.impl.AeadConfigCipherImpl
import family.keys.impl.KeyHierarchy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Phase 3 US1 roundtrip + identity-binding tests для ConfigCipher (T052, T055, T057, FR-016, FR-020, FR-029).
 *
 * Использует JVM in-memory SecureKeyStore (test-only) — production Android Keystore
 * проверяется в instrumented tests (Lane B).
 */
class ConfigCipherRoundtripTest {

    private val uid = "test-uid-abc-123"
    private val identity = AuthIdentity(stableId = uid, displayName = null, email = null)

    private suspend fun makeCipher(): AeadConfigCipherImpl {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
        val keyStore = SecureKeyStore(KeyStoreContext())
        val aead = LibsodiumAeadCipher()
        val random = LibsodiumRandomSource()
        val hierarchy = KeyHierarchy(uid, keyStore, aead, random)
        val boot = hierarchy.bootstrap(identity)
        assertIs<Outcome.Success<*>>(boot)
        return AeadConfigCipherImpl(aead, hierarchy.keyRegistry)
    }

    @Test
    fun roundtripSmall() = runTest {
        val cipher = makeCipher()
        val plaintext = "config payload".encodeToByteArray()
        val sealed = (cipher.seal(plaintext, uid) as Outcome.Success<SealedConfig>).value
        assertEquals(SealedConfig.SCHEMA_VERSION, sealed.schemaVersion)
        assertEquals(SealedConfig.ALGORITHM_V1, sealed.algorithm)
        assertEquals(24, sealed.nonce.size)
        val opened = (cipher.open(sealed, uid) as Outcome.Success<ByteArray>).value
        assertContentEquals(plaintext, opened)
    }

    @Test
    fun roundtripLargeFixtures() = runTest {
        val cipher = makeCipher()
        val cases = listOf(
            ByteArray(1) { 0x01 },
            ByteArray(16) { it.toByte() },
            ByteArray(1024) { (it % 251).toByte() },
            ByteArray(64 * 1024) { ((it * 7) % 251).toByte() },
            "{\"contacts\":[{\"name\":\"Bobby Tables\",\"phone\":\"555-1234\"}]}".encodeToByteArray()
        )
        for (pt in cases) {
            val sealed = (cipher.seal(pt, uid) as Outcome.Success<SealedConfig>).value
            val opened = (cipher.open(sealed, uid) as Outcome.Success<ByteArray>).value
            assertContentEquals(pt, opened, "Roundtrip failed for size ${pt.size}")
        }
    }

    @Test
    fun ciphertextDoesNotContainPlaintextSubstring() = runTest {
        val cipher = makeCipher()
        val marker = "Bobby Tables 555-1234".encodeToByteArray()
        val plaintext = ("prefix:" + marker.decodeToString() + ":suffix").encodeToByteArray()
        val sealed = (cipher.seal(plaintext, uid) as Outcome.Success<SealedConfig>).value

        // SealedConfig ciphertext не должен содержать plaintext fragment'ы.
        // Это grep test analog SC-001 (FR-018) на чистом JVM уровне, без Firestore.
        for (i in 0..sealed.ciphertext.size - marker.size) {
            val slice = sealed.ciphertext.copyOfRange(i, i + marker.size)
            assertNotEquals(
                marker.toList(),
                slice.toList(),
                "Plaintext marker found at offset $i in ciphertext — encryption fails opacity goal (FR-018)"
            )
        }
    }

    @Test
    fun openWithDifferentUidFailsAead() = runTest {
        val cipher = makeCipher()
        val plaintext = "secret".encodeToByteArray()
        val sealed = (cipher.seal(plaintext, uid) as Outcome.Success<SealedConfig>).value

        val wrong = cipher.open(sealed, "uid-different-from-original")
        assertIs<Outcome.Failure<CipherError>>(wrong)
        assertEquals(CipherError.AeadAuthFailed, wrong.error)
    }

    @Test
    fun openWithTamperedCiphertextFailsAead() = runTest {
        val cipher = makeCipher()
        val plaintext = "important".encodeToByteArray()
        val sealed = (cipher.seal(plaintext, uid) as Outcome.Success<SealedConfig>).value

        val tampered = sealed.copy(
            ciphertext = sealed.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        )
        val opened = cipher.open(tampered, uid)
        assertIs<Outcome.Failure<CipherError>>(opened)
        assertEquals(CipherError.AeadAuthFailed, opened.error)
    }

    @Test
    fun sealRejectsOversizedConfig() = runTest {
        val cipher = makeCipher()
        val tooBig = ByteArray(AeadConfigCipherImpl.MAX_CONFIG_BYTES + 1)
        val result = cipher.seal(tooBig, uid)
        assertIs<Outcome.Failure<CipherError>>(result)
        assertEquals(CipherError.ConfigTooLarge, result.error)
    }

    @Test
    fun sealAtMaxSizeAllowed() = runTest {
        val cipher = makeCipher()
        val atMax = ByteArray(AeadConfigCipherImpl.MAX_CONFIG_BYTES) { (it % 251).toByte() }
        val sealed = (cipher.seal(atMax, uid) as Outcome.Success<SealedConfig>).value
        val opened = (cipher.open(sealed, uid) as Outcome.Success<ByteArray>).value
        assertContentEquals(atMax, opened)
    }

    @Test
    fun openWithFutureSchemaVersionReturnsAlgorithmUnsupported() = runTest {
        val cipher = makeCipher()
        val sealed = (cipher.seal("x".encodeToByteArray(), uid) as Outcome.Success<SealedConfig>).value

        val futureVersion = sealed.copy(schemaVersion = SealedConfig.SCHEMA_VERSION + 99)
        val opened = cipher.open(futureVersion, uid)
        assertIs<Outcome.Failure<CipherError>>(opened)
        assertEquals(CipherError.AlgorithmUnsupported, opened.error)
    }

    @Test
    fun openWithUnknownAlgorithmReturnsAlgorithmUnsupported() = runTest {
        val cipher = makeCipher()
        val sealed = (cipher.seal("x".encodeToByteArray(), uid) as Outcome.Success<SealedConfig>).value

        val unknownAlg = sealed.copy(algorithm = "kyber-768-experimental")
        val opened = cipher.open(unknownAlg, uid)
        assertIs<Outcome.Failure<CipherError>>(opened)
        assertEquals(CipherError.AlgorithmUnsupported, opened.error)
    }

    @Test
    fun sealProducesDifferentCiphertextEachCall() = runTest {
        val cipher = makeCipher()
        val plaintext = "deterministic-ish?".encodeToByteArray()
        val a = (cipher.seal(plaintext, uid) as Outcome.Success<SealedConfig>).value
        val b = (cipher.seal(plaintext, uid) as Outcome.Success<SealedConfig>).value
        assertTrue(!a.nonce.contentEquals(b.nonce), "Nonce must differ between seal calls")
        assertTrue(!a.ciphertext.contentEquals(b.ciphertext), "Ciphertext must differ (nonce-based)")
    }
}
