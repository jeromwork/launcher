package family.keys

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import family.crypto.api.AeadCipher
import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import family.crypto.api.values.Ciphertext
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumArgon2idPasswordHash
import family.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.AuthIdentity
import family.keys.api.CipherError
import family.keys.api.Outcome
import family.keys.api.PassphraseKdfParams
import family.keys.api.RootKey
import family.keys.api.SealedConfig
import family.keys.fakes.FakePassphrasePrompter
import family.keys.fakes.FakeRecoveryKeyVault
import family.keys.fakes.InMemoryAttemptCounter
import family.keys.impl.AeadConfigCipherImpl
import family.keys.impl.Argon2idPassphraseKdf
import family.keys.impl.KeyHierarchy
import family.keys.impl.RecoveryFlow
import family.keys.impl.RootKeyManagerImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Phase 7 security-hardening tests:
 *  • T088a — DerivedKeyZeroizeTest: 32-byte output Argon2id обнулён после wrap/unwrap (G-1).
 *  • T122c — AadSchemaVersionEarlyValidationTest: unsupported schemaVersion → reject
 *    ДО invocation AEAD layer (H-3 — защита от попытки decrypt'а будущей версии).
 *
 * Оба теста используют [CapturingAead] decorator, который сохраняет references на
 * key ByteArrays и считает вызовы encrypt/decrypt без модификации delegate'а.
 */
class F5SecurityHardeningTest {

    private val uid = "uid-security-test"
    private val identity = AuthIdentity(stableId = uid, displayName = null, email = null)

    /** Lightweight Argon2id params для test speed. */
    private val fastParams = PassphraseKdfParams(memoryKib = 8192, iterations = 1)

    // -----------------------------------------------------------------------
    // T088a — Derived wrap-key zeroize
    // -----------------------------------------------------------------------

    @Test
    fun derivedWrapKeyZeroizedAfterSetupWrap() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val realAead = LibsodiumAeadCipher()
        val capturing = CapturingAead(realAead)

        val keystore = SecureKeyStore(KeyStoreContext())
        val random = LibsodiumRandomSource()
        val rootMgr = RootKeyManagerImpl(keystore, random, realAead)
        val vault = FakeRecoveryKeyVault()
        val kdf = Argon2idPassphraseKdf(LibsodiumArgon2idPasswordHash())
        val prompter = FakePassphrasePrompter()
        val counter = InMemoryAttemptCounter()
        val flow = RecoveryFlow(
            rootKeyManager = rootMgr,
            vault = vault,
            kdf = kdf,
            aead = capturing,
            random = random,
            prompter = prompter,
            attemptCounter = counter,
            setupKdfParams = fastParams
        )
        val root = (rootMgr.getOrCreate(identity) as Outcome.Success<RootKey>).value
        prompter.enqueueSetup("zeroize-test-passphrase")

        val setup = flow.performSetup(identity, root)
        assertIs<Outcome.Success<Unit>>(setup)

        // CapturingAead saved a REFERENCE to the wrap key — RecoveryFlow.performSetup
        // is expected to .fill(0) it in finally{} (G-1). После return — все 32 байта 0.
        val captured = assertNotNull(capturing.lastEncryptKey, "encrypt() must have been called")
        assertEquals(32, captured.size, "Argon2id output должен быть 32 байта")
        assertTrue(
            captured.all { it == 0.toByte() },
            "G-1: derived 32-byte wrapKey MUST be zeroized after performSetup returns. " +
                "Captured bytes (first 8): ${captured.take(8).joinToString(",") { it.toString() }}"
        )
    }

    @Test
    fun derivedWrapKeyZeroizedAfterRecoveryUnwrap() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val realAead = LibsodiumAeadCipher()
        val keystore = SecureKeyStore(KeyStoreContext())
        val random = LibsodiumRandomSource()

        // Setup phase: prepare a valid vault blob (use plain AEAD, no capture).
        val mgrA = RootKeyManagerImpl(keystore, random, realAead)
        val vault = FakeRecoveryKeyVault()
        val kdf = Argon2idPassphraseKdf(LibsodiumArgon2idPasswordHash())
        val prompterA = FakePassphrasePrompter()
        val counter = InMemoryAttemptCounter()
        val setupFlow = RecoveryFlow(
            rootKeyManager = mgrA,
            vault = vault,
            kdf = kdf,
            aead = realAead,
            random = random,
            prompter = prompterA,
            attemptCounter = counter,
            setupKdfParams = fastParams
        )
        val rootA = (mgrA.getOrCreate(identity) as Outcome.Success<RootKey>).value
        prompterA.enqueueSetup("unwrap-zeroize-test")
        assertIs<Outcome.Success<Unit>>(setupFlow.performSetup(identity, rootA))

        // Recovery phase on a fresh "device": wrap AEAD to capture unwrap key.
        val capturing = CapturingAead(realAead)
        val keystoreB = SecureKeyStore(KeyStoreContext())
        val mgrB = RootKeyManagerImpl(keystoreB, random, realAead)
        val prompterB = FakePassphrasePrompter()
        val counterB = InMemoryAttemptCounter()
        val recoveryFlow = RecoveryFlow(
            rootKeyManager = mgrB,
            vault = vault,
            kdf = kdf,
            aead = capturing,
            random = random,
            prompter = prompterB,
            attemptCounter = counterB,
            setupKdfParams = fastParams
        )
        prompterB.enqueueRecovery("unwrap-zeroize-test")
        val recovered = recoveryFlow.performRecovery(identity)
        assertIs<Outcome.Success<RootKey>>(recovered)

        val captured = assertNotNull(capturing.lastDecryptKey, "decrypt() must have been called")
        assertEquals(32, captured.size)
        assertTrue(
            captured.all { it == 0.toByte() },
            "G-1: derived wrapKey used for unwrap MUST be zeroized after performRecovery returns"
        )
    }

    // -----------------------------------------------------------------------
    // T122c — Early schema-version validation (H-3)
    // -----------------------------------------------------------------------

    @Test
    fun openWithFutureSchemaVersionDoesNotInvokeAeadDecrypt() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val realAead = LibsodiumAeadCipher()
        val capturing = CapturingAead(realAead)
        val keystore = SecureKeyStore(KeyStoreContext())
        val random = LibsodiumRandomSource()
        val hierarchy = KeyHierarchy(uid, keystore, realAead, random)
        assertIs<Outcome.Success<*>>(hierarchy.bootstrap(identity))
        val cipher = AeadConfigCipherImpl(capturing, hierarchy.keyRegistry)

        // 1) Produce a legitimate sealed blob and prove decrypt counter increments
        //    on a normal open() call (sanity baseline).
        val sealed = (cipher.seal("payload".encodeToByteArray(), uid) as Outcome.Success<SealedConfig>).value
        capturing.resetCounters()
        val openOk = cipher.open(sealed, uid)
        assertIs<Outcome.Success<ByteArray>>(openOk)
        assertEquals(1, capturing.decryptCalls, "sanity: legitimate open() invokes AEAD.decrypt once")

        // 2) Forge a future-version blob and expect rejection BEFORE AEAD.decrypt is touched.
        capturing.resetCounters()
        val futureBlob = sealed.copy(schemaVersion = SealedConfig.SCHEMA_VERSION + 7)
        val rejected = cipher.open(futureBlob, uid)
        assertIs<Outcome.Failure<CipherError>>(rejected)
        assertEquals(CipherError.AlgorithmUnsupported, rejected.error)
        assertEquals(
            0, capturing.decryptCalls,
            "H-3: AEAD.decrypt MUST NOT be invoked for unsupported schemaVersion. " +
                "Calls observed: ${capturing.decryptCalls}"
        )
    }

    @Test
    fun openWithUnknownAlgorithmDoesNotInvokeAeadDecrypt() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val realAead = LibsodiumAeadCipher()
        val capturing = CapturingAead(realAead)
        val keystore = SecureKeyStore(KeyStoreContext())
        val random = LibsodiumRandomSource()
        val hierarchy = KeyHierarchy(uid, keystore, realAead, random)
        assertIs<Outcome.Success<*>>(hierarchy.bootstrap(identity))
        val cipher = AeadConfigCipherImpl(capturing, hierarchy.keyRegistry)

        val sealed = (cipher.seal("payload".encodeToByteArray(), uid) as Outcome.Success<SealedConfig>).value
        capturing.resetCounters()
        val forged = sealed.copy(algorithm = "post-quantum-kyber-experimental")
        val rejected = cipher.open(forged, uid)
        assertIs<Outcome.Failure<CipherError>>(rejected)
        assertEquals(CipherError.AlgorithmUnsupported, rejected.error)
        assertEquals(
            0, capturing.decryptCalls,
            "H-3: AEAD.decrypt MUST NOT be invoked for unknown algorithm string."
        )
    }
}

/**
 * Spy [AeadCipher] decorator: capture the most recent `key` ByteArray reference passed
 * to encrypt/decrypt and count invocations. Delegates real work to a real adapter, so
 * roundtrip semantics остаются корректными.
 *
 * Используется в двух категориях security-тестов:
 *  1. **Zeroize**: после возврата из caller'а проверяем, что captured ByteArray заполнен 0
 *     (G-1 — derived key memory hygiene).
 *  2. **Defensive validation**: проверяем, что caller вообще не вызвал decrypt, когда
 *     должен был отказать раньше (H-3 — early reject of unsupported wire formats).
 */
private class CapturingAead(private val delegate: AeadCipher) : AeadCipher {
    var lastEncryptKey: ByteArray? = null
        private set
    var lastDecryptKey: ByteArray? = null
        private set
    var encryptCalls: Int = 0
        private set
    var decryptCalls: Int = 0
        private set

    fun resetCounters() {
        encryptCalls = 0
        decryptCalls = 0
        lastEncryptKey = null
        lastDecryptKey = null
    }

    override suspend fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray): Ciphertext {
        encryptCalls++
        lastEncryptKey = key
        // assertSame is a debug-time sanity hint: confirms our captured reference is the
        // very same instance the caller passed in (no defensive copy elsewhere).
        assertSame(key, lastEncryptKey)
        return delegate.encrypt(plaintext, key, aad)
    }

    override suspend fun decrypt(ciphertext: Ciphertext, key: ByteArray, aad: ByteArray): ByteArray {
        decryptCalls++
        lastDecryptKey = key
        return delegate.decrypt(ciphertext, key, aad)
    }
}
