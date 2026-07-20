package family.crypto

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import family.crypto.api.values.KeyId
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumAsymmetricCrypto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

/**
 * TASK-51 Phase 9 T102 — Spec011SmokeDebugActivity Phase A round-trip equivalent.
 *
 * Mirrors the work that Spec011 debug Activity performs in `onCreate` (ensureKeys)
 * and on the "Run self-roundtrip (16 random bytes)" button (Phase A). Runs on a
 * real device so the libsodium .so + AndroidKeystore TEE wrap path are exercised
 * end-to-end — same code paths as the production PairingCryptoCoordinator.
 */
@RunWith(AndroidJUnit4::class)
class Spec011RoundtripSmokeTest {

    private val ctx = KeyStoreContext(ApplicationProvider.getApplicationContext())
    private val store = SecureKeyStore(ctx)
    private val aead = LibsodiumAeadCipher()
    private val asymm = LibsodiumAsymmetricCrypto()
    private val testKeyId = KeyId("__internal-spec011-smoke-key-v1")

    @After
    fun cleanup() = runTest {
        store.delete(testKeyId)
    }

    @Test
    fun phaseAselfRoundtrip_16RandomBytes() = runTest {
        val plaintext = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val symKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val ciphertext = aead.encrypt(plaintext, symKey)
        val decrypted = aead.decrypt(ciphertext, symKey)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun ensureKeys_generateX25519AndEd25519AndPersist() = runTest {
        val x = asymm.generateX25519KeyPair()
        val e = asymm.generateEd25519KeyPair()
        assertEquals(32, x.publicKey.size)
        assertEquals(32, e.publicKey.size)
        assertNotEquals(0, x.privateKey.size)
        store.store(testKeyId, x.privateKey)
        val loaded = store.load(testKeyId)
        assertArrayEquals(x.privateKey, loaded)
    }
}
