package cryptokit.keys.impl.vault

import android.content.Context
import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.KeyDerivation
import cryptokit.crypto.api.KeyStoreContext
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.crypto.api.values.KeyId
import cryptokit.crypto.exception.CryptoException
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumAsymmetricCrypto
import cryptokit.crypto.libsodium.LibsodiumKeyDerivation
import cryptokit.keys.api.vault.KeyVault
import cryptokit.keys.api.vault.RecoveryStrategy
import cryptokit.keys.api.vault.VaultException

/**
 * Android [KeyVault] adapter — inherits crypto ops from [KeyVaultCore] and adds Android
 * Keystore persistence for `root_key` at rest (FR-010).
 *
 * ### Root at rest
 * `root_key` bytes are wrapped by an AES-256-GCM key living in Android Keystore (via
 * [SecureKeyStore], reused from `:core:crypto`) and persisted to `<filesDir>/keys/{keyId}.blob`.
 * On app restart the vault can re-hydrate without asking the user for a passphrase — matches
 * Bitwarden/1Password auto-unlock UX. The device lock (PIN / biometric) is the outer boundary.
 *
 * ### Unlock flow
 *  1. If [SecureKeyStore] already holds a wrapped root under [ROOT_KEY_ID] → load it, bootstrap
 *     the vault in-memory, ignore the supplied [RecoveryStrategy]. Argon2id is skipped —
 *     re-hydration is instant.
 *  2. Otherwise → run `strategy.deriveRoot()`, `strategy.verifyUnlock(...)`, persist the raw
 *     root to [SecureKeyStore], then bootstrap in-memory.
 *
 * ### Wipe
 * Clears in-memory state (via [KeyVaultCore.wipeInMemory]) AND deletes the persistent blob
 * from [SecureKeyStore]. Subsequent operations throw [VaultException.NoRootKey] until the next
 * successful [unlock] (which will run the strategy since the persistent blob is gone).
 *
 * ### Failure modes
 *  * Android Keystore unavailable / invalidated → [VaultException.HardwareBackedKeystoreUnavailable].
 *  * Recovery strategy rejects the passphrase → [VaultException.RecoveryFailed] (bubbles from
 *    the underlying [KeyVaultCore.unlock] path).
 */
class AndroidKeyVault(
    context: Context,
    private val secureKeyStore: SecureKeyStore = SecureKeyStore(KeyStoreContext(context.applicationContext)),
    aead: AeadCipher = LibsodiumAeadCipher(),
    kdf: KeyDerivation = LibsodiumKeyDerivation(),
    asym: AsymmetricCrypto = LibsodiumAsymmetricCrypto(),
    validationStore: ValidationBlobStore = AndroidValidationBlobStore(context.applicationContext),
) : KeyVaultCore(aead, kdf, asym, validationStore), KeyVault {

    private val rootKeyId = KeyId(ROOT_KEY_ID)

    override suspend fun unlock(strategy: RecoveryStrategy) {
        val existing = try {
            secureKeyStore.load(rootKeyId)
        } catch (e: CryptoException.KeystoreUnavailable) {
            throw VaultException.HardwareBackedKeystoreUnavailable("Android Keystore unavailable", e)
        } catch (e: CryptoException.KeystoreInvalidated) {
            // Wrap-key invalidation means our persisted root is unrecoverable — treat as if the
            // vault had been wiped. Fall through to the strategy path so the user can re-derive.
            try { secureKeyStore.delete(rootKeyId) } catch (_: Throwable) { /* best-effort */ }
            null
        }
        if (existing != null) {
            bootstrapWithRoot(existing, wipeInput = true)
            return
        }
        // First unlock (or post-wipe) — derive via strategy, verify, then persist.
        val derived = strategy.deriveRoot()
        try {
            strategy.verifyUnlock(derived)
        } catch (t: Throwable) {
            derived.fill(0)
            throw t
        }
        try {
            secureKeyStore.store(rootKeyId, derived)
        } catch (e: CryptoException.KeystoreUnavailable) {
            derived.fill(0)
            throw VaultException.HardwareBackedKeystoreUnavailable("Cannot persist root to Keystore", e)
        }
        bootstrapWithRoot(derived, wipeInput = true)
    }

    override suspend fun wipe() {
        wipeInMemory()
        try {
            secureKeyStore.delete(rootKeyId)
        } catch (_: Throwable) {
            // Idempotent by contract (FR-006c). Best-effort cleanup; in-memory is already gone.
        }
    }

    companion object {
        /**
         * Fixed [KeyId] under which the wrapped root is persisted. Uses the framework-internal
         * `__internal-` namespace prefix per `KeyNamespace` validation rules — this key belongs
         * to the KeyVault infrastructure itself, not to any application-level Purpose.
         */
        const val ROOT_KEY_ID: String = "__internal-keyvault-root-v1"
    }
}
