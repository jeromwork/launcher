package cryptokit.keys.impl.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cryptokit.crypto.api.KeyStoreContext
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.keys.api.vault.IdentityHint
import cryptokit.keys.api.vault.PassphraseRecovery
import cryptokit.keys.api.vault.Purpose
import cryptokit.keys.api.vault.VaultException
import cryptokit.keys.api.vault.canonicalAad
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumArgon2idPasswordHash
import cryptokit.crypto.libsodium.LibsodiumKeyDerivation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device integration tests for [AndroidKeyVault] — exercises Android Keystore + JNI
 * libsodium end-to-end (T016, SC-011).
 *
 * These tests require an Android emulator (or physical device) — run via
 * `./gradlew :core:keys:connectedAndroidTest` under the `android-emulator` skill.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeyVaultIntegrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val hint = IdentityHint.GoogleAccount("uid-integration-test")

    @After
    fun cleanup() = runBlocking {
        // Wipe any leftover keystore blob so the next test starts fresh.
        val ks = SecureKeyStore(KeyStoreContext(context.applicationContext))
        try {
            ks.delete(cryptokit.crypto.api.values.KeyId(AndroidKeyVault.ROOT_KEY_ID))
        } catch (_: Throwable) { /* best effort */ }
        // Clear validation blobs.
        context.getSharedPreferences(AndroidValidationBlobStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun buildVault() = AndroidKeyVault(context.applicationContext)

    private fun buildStrategy(passphrase: String, ctx: Context) = PassphraseRecovery(
        passphrase = passphrase.toCharArray(),
        identityHint = hint,
        passwordHash = LibsodiumArgon2idPasswordHash(),
        keyDerivation = LibsodiumKeyDerivation(),
        aead = LibsodiumAeadCipher(),
        store = AndroidValidationBlobStore(ctx),
    )

    @Test
    fun unlock_seal_open_roundtrip() = runBlocking {
        val vault = buildVault()
        vault.unlock(buildStrategy("myPassphrase", context))
        val aad = canonicalAad("ns", 1, 1)
        val plaintext = "sensitive config".encodeToByteArray()
        val ct = vault.aeadSeal(Purpose.CONFIG, plaintext, aad)
        val out = vault.aeadOpen(Purpose.CONFIG, ct, aad)
        assertArrayEquals(plaintext, out)
    }

    @Test
    fun reload_after_app_restart_reuses_persisted_root() = runBlocking {
        val v1 = buildVault()
        v1.unlock(buildStrategy("myPassphrase", context))
        val aad = canonicalAad("ns", 1, 1)
        val ct = v1.aeadSeal(Purpose.CONFIG, "restart-me".encodeToByteArray(), aad)

        // Simulate app restart: new vault instance, no strategy invocation should be needed
        // beyond bootstrap from Keystore.
        val v2 = buildVault()
        v2.unlock(buildStrategy("SHOULD-NOT-MATTER", context))
        val out = v2.aeadOpen(Purpose.CONFIG, ct, aad)
        assertArrayEquals("restart-me".encodeToByteArray(), out)
    }

    @Test
    fun wipe_deletes_keystore_and_subsequent_op_throws_NoRootKey() = runBlocking {
        val vault = buildVault()
        vault.unlock(buildStrategy("p", context))
        val aad = canonicalAad("ns", 1, 1)
        val ct = vault.aeadSeal(Purpose.CONFIG, "hi".encodeToByteArray(), aad)
        vault.wipe()

        var threwNoRoot = false
        try {
            vault.aeadOpen(Purpose.CONFIG, ct, aad)
        } catch (e: VaultException.NoRootKey) {
            threwNoRoot = true
        }
        assertTrue("wipe cascade must throw NoRootKey", threwNoRoot)
    }

    @Test
    fun wrong_passphrase_after_setup_throws_RecoveryFailed() = runBlocking {
        // Setup vault.
        val v1 = buildVault()
        v1.unlock(buildStrategy("correctPassphrase", context))
        v1.wipe() // remove persistent root but keep validation blob

        // Wipe deletes SecureKeyStore entry but AndroidValidationBlobStore retains the blob,
        // which means next unlock will run the strategy and validate — wrong passphrase fails.
        val v2 = buildVault()
        var thrown = false
        try {
            v2.unlock(buildStrategy("WRONG-passphrase", context))
        } catch (e: VaultException.RecoveryFailed) {
            thrown = true
        }
        assertTrue("wrong passphrase must throw RecoveryFailed", thrown)
    }
}
