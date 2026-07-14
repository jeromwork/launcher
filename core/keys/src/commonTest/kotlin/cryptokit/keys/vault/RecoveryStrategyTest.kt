package cryptokit.keys.vault

import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumArgon2idPasswordHash
import cryptokit.crypto.libsodium.LibsodiumKeyDerivation
import cryptokit.keys.api.vault.IdentityHint
import cryptokit.keys.api.vault.PassphraseRecovery
import cryptokit.keys.api.vault.Purpose
import cryptokit.keys.api.vault.TestRecoveryStrategy
import cryptokit.keys.api.vault.VaultException
import cryptokit.keys.api.vault.canonicalAad
import cryptokit.keys.impl.vault.InMemoryValidationBlobStore
import cryptokit.keys.impl.vault.ValidationBlobStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * FR-005 extensibility check + FR-006 / FR-006b / SC-013 / SC-014 for [PassphraseRecovery].
 *
 * Tests deliberately share the same [ValidationBlobStore] between "setup" and "recover" vaults
 * to mimic what an on-disk store does in production (Android's `EncryptedSharedPreferences`
 * or the future iOS Keychain).
 */
class RecoveryStrategyTest {

    private fun buildStrategy(
        passphrase: String,
        hint: IdentityHint,
        store: ValidationBlobStore,
    ): PassphraseRecovery = PassphraseRecovery(
        passphrase = passphrase.toCharArray(),
        identityHint = hint,
        passwordHash = LibsodiumArgon2idPasswordHash(),
        keyDerivation = LibsodiumKeyDerivation(),
        aead = LibsodiumAeadCipher(),
        store = store,
    )

    @Test
    fun `TestRecoveryStrategy plug-in works without KeyVault changes`() = runTest {
        val vault = FakeKeyVault()
        vault.unlock(TestRecoveryStrategy())
        val aad = canonicalAad("ns", 1, 1)
        val ct = vault.aeadSeal(Purpose.CONFIG, "payload".encodeToByteArray(), aad)

        val v2 = FakeKeyVault()
        v2.unlock(TestRecoveryStrategy())
        val opened = v2.aeadOpen(Purpose.CONFIG, ct, aad)
        assertContentEquals("payload".encodeToByteArray(), opened)
    }

    @Test
    fun `SC-013 wrong passphrase throws RecoveryFailed at unlock — not silent at aeadOpen`() = runTest {
        val store = InMemoryValidationBlobStore()
        val hint = IdentityHint.GoogleAccount("uid-fixed-12345")
        val setup = buildStrategy("correct-passphrase", hint, store)

        val vault = FakeKeyVault(store = store)
        vault.unlock(setup)

        val vault2 = FakeKeyVault(store = store)
        val wrong = buildStrategy("wrong-passphrase", hint, store)
        assertFailsWith<VaultException.RecoveryFailed> {
            vault2.unlock(wrong)
        }
    }

    @Test
    fun `correct passphrase re-unlocks and decrypts previously sealed data`() = runTest {
        val store = InMemoryValidationBlobStore()
        val hint = IdentityHint.GoogleAccount("uid-fixed-67890")

        val vault = FakeKeyVault(store = store)
        vault.unlock(buildStrategy("mySecret123", hint, store))
        val aad = canonicalAad("ns", 1, 1)
        val ct = vault.aeadSeal(Purpose.CONFIG, "payload".encodeToByteArray(), aad)

        val vault2 = FakeKeyVault(store = store)
        vault2.unlock(buildStrategy("mySecret123", hint, store))
        val opened = vault2.aeadOpen(Purpose.CONFIG, ct, aad)
        assertContentEquals("payload".encodeToByteArray(), opened)
    }

    @Test
    fun `SC-014 salt derivation from GoogleAccount is deterministic across strategy instances`() = runTest {
        val store = InMemoryValidationBlobStore()
        val hint = IdentityHint.GoogleAccount("same-uid")
        val a = buildStrategy("password", hint, store).deriveRoot()
        val b = buildStrategy("password", hint, store).deriveRoot()
        assertContentEquals(a, b)
    }

    @Test
    fun `NoGmsDevice path with different random salts yields different roots`() = runTest {
        val store = InMemoryValidationBlobStore()
        val salt1 = ByteArray(IdentityHint.SALT_SIZE) { 0x11 }
        val salt2 = ByteArray(IdentityHint.SALT_SIZE) { 0x22 }
        val r1 = buildStrategy("password", IdentityHint.NoGmsDevice(salt1), store).deriveRoot()
        val r2 = buildStrategy("password", IdentityHint.NoGmsDevice(salt2), store).deriveRoot()
        assertNotEquals(r1.toList(), r2.toList())
    }

    @Test
    fun `first setup stores validation blob, subsequent unlock finds it`() = runTest {
        val store = InMemoryValidationBlobStore()
        val hint = IdentityHint.GoogleAccount("uid-check-store")
        val hintKey = PassphraseRecovery.hintKey(hint)
        assertTrue(store.read(hintKey) == null, "store should be empty initially")

        FakeKeyVault(store = store).unlock(buildStrategy("p", hint, store))
        assertTrue(store.read(hintKey) != null, "validation blob must persist after unlock")
    }
}
