package family.keys

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.api.RootKey
import family.keys.api.SealedConfig
import family.keys.impl.AeadConfigCipherImpl
import family.keys.impl.KeyHierarchy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 7 Lane C — F-5 roundtrip против реального Android Keystore (T126/T127).
 *
 * Цели:
 *  • Verify, что RootKeyManagerImpl + KeyRegistryImpl + AeadConfigCipherImpl
 *    работают на реальном устройстве (включая MIUI, где Keystore поведение
 *    может отличаться).
 *  • Detect known MIUI issues:
 *    - Xiaomi "Optimize MIUI" может clear'ить Keystore aliases — verify
 *      что mapping wrapped key → восстанавливается из persistent storage.
 *    - StrictMode + Keystore + UI thread (не проверяется здесь — UI test).
 *
 * Запускается на любом Android устройстве (emulator или физ.).
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreF5RoundtripTest {

    private val uid = "f5-test-uid-${System.currentTimeMillis()}"
    private val identity = AuthIdentity(uid, null, null)
    private lateinit var keystoreContext: KeyStoreContext
    private lateinit var hierarchy: KeyHierarchy
    private lateinit var cipher: AeadConfigCipherImpl

    @Before
    fun setup() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        keystoreContext = KeyStoreContext(ApplicationProvider.getApplicationContext())
        val secureKeyStore = SecureKeyStore(keystoreContext)
        val aead = LibsodiumAeadCipher()
        val random = LibsodiumRandomSource()
        hierarchy = KeyHierarchy(uid, secureKeyStore, aead, random)
        val boot = hierarchy.bootstrap(identity)
        assertTrue("Bootstrap должен пройти на реальном устройстве", boot is Outcome.Success)
        cipher = AeadConfigCipherImpl(aead, hierarchy.keyRegistry)
    }

    @After
    fun cleanup() = runTest {
        hierarchy.rootKeyManager.wipe(identity)
    }

    @Test
    fun configCipherRoundtripOnRealKeystore() = runTest {
        val plaintext = "real-device test payload — bobby tables 555-1234".encodeToByteArray()
        val sealed = (cipher.seal(plaintext, uid) as Outcome.Success<SealedConfig>).value
        val opened = (cipher.open(sealed, uid) as Outcome.Success<ByteArray>).value
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun rootKeyPersistsAcrossKeyHierarchyInstances() = runTest {
        // Имитируем app restart: создаём свежий KeyHierarchy на том же
        // KeyStoreContext + same uid → root key должен load'нуться из Keystore,
        // а не regenerate'нуться.
        val secureKeyStore2 = SecureKeyStore(keystoreContext)
        val aead = LibsodiumAeadCipher()
        val random = LibsodiumRandomSource()
        val hierarchy2 = KeyHierarchy(uid, secureKeyStore2, aead, random)
        val boot2 = hierarchy2.bootstrap(identity)
        assertTrue(boot2 is Outcome.Success)

        // Test: seal под hierarchy1 → open под hierarchy2 = byte-equal.
        val plaintext = "persistence test".encodeToByteArray()
        val sealed = (cipher.seal(plaintext, uid) as Outcome.Success<SealedConfig>).value
        val cipher2 = AeadConfigCipherImpl(aead, hierarchy2.keyRegistry)
        val opened = (cipher2.open(sealed, uid) as Outcome.Success<ByteArray>).value
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun multipleDeksRegisteredAndRetrievedOnRealKeystore() = runTest {
        // Реальный test для FR-004 + FR-023 multi-DEK scenario на устройстве.
        val dek1 = ByteArray(32) { 0x11 }
        val dek2 = ByteArray(32) { 0x22 }
        val r1 = hierarchy.keyRegistry.registerDek("test-pair-dek-v1", dek1)
        val r2 = hierarchy.keyRegistry.registerDek("test-photo-dek-v1", dek2)
        assertTrue(r1 is Outcome.Success)
        assertTrue(r2 is Outcome.Success)

        val got1 = (hierarchy.keyRegistry.getDek("test-pair-dek-v1") as Outcome.Success<ByteArray>).value
        val got2 = (hierarchy.keyRegistry.getDek("test-photo-dek-v1") as Outcome.Success<ByteArray>).value
        assertArrayEquals(dek1, got1)
        assertArrayEquals(dek2, got2)
    }

    @Test
    fun rootKeyWipeRemovesFromKeystore() = runTest {
        // После wipe → getOrCreate должен сгенерить новый root (не вернуть старый).
        val rootBytes1Holder: RootKey = (hierarchy.rootKeyManager.getOrCreate(identity) as Outcome.Success<RootKey>).value
        val bytes1 = rootBytes1Holder.bytes.copyOf()

        val wipe = hierarchy.rootKeyManager.wipe(identity)
        assertTrue(wipe is Outcome.Success)

        // Fresh hierarchy чтобы избежать in-process cache (которая у нас persistent).
        val secureKeyStore2 = SecureKeyStore(keystoreContext)
        val aead = LibsodiumAeadCipher()
        val random = LibsodiumRandomSource()
        val hierarchy2 = KeyHierarchy(uid, secureKeyStore2, aead, random)
        val rootBytes2 = (hierarchy2.rootKeyManager.getOrCreate(identity) as Outcome.Success<RootKey>).value.bytes.copyOf()

        // Bytes должны быть разными — старый root удалён.
        assertNotNull(rootBytes2)
        assertEquals(RootKey.SIZE, rootBytes2.size)
        assertTrue(
            "После wipe root key должен быть новый (не равен старому)",
            !rootBytes2.contentEquals(bytes1)
        )
    }
}
