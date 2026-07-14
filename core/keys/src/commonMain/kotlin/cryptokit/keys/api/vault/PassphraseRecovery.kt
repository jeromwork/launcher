package cryptokit.keys.api.vault

import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.KeyDerivation
import cryptokit.crypto.api.PasswordHash
import cryptokit.crypto.api.values.Ciphertext as CryptoCiphertext
import cryptokit.crypto.exception.CryptoException
import cryptokit.keys.impl.vault.Argon2Params
import cryptokit.keys.impl.vault.RootKey
import cryptokit.keys.impl.vault.ValidationBlobStore

/**
 * MVP [RecoveryStrategy]: `root = Argon2id(passphrase, salt, Argon2Params.V1)`.
 *
 * Lives in the same package as [RecoveryStrategy] because the base is `sealed`. Adding future
 * strategies (BIP39, hardware key, social recovery) means adding files to this package — no
 * other module can invent one.
 *
 * ### Salt derivation (Bitwarden pattern, Session 7 Q-B)
 *  * [IdentityHint.GoogleAccount] → `salt = HKDF(googleUid.utf8, info="salt-v1", 16 bytes)`.
 *    Deterministic; recovering on a fresh device signed into the same Google account regenerates
 *    the identical salt without any prior device state.
 *  * [IdentityHint.NoGmsDevice] → `salt = deviceRandomSalt` (16 bytes CSPRNG generated at first
 *    setup, stored device-local). Cross-device recovery on no-GMS builds needs an external
 *    salt-transfer channel (out of scope for TASK-112 MVP — see server-roadmap).
 *
 * ### Validation (Q-D D1)
 * First `unlock` on a fresh vault: [verifyUnlock] finds no validation blob in [store] and seals
 * one containing [VALIDATION_PLAINTEXT] under the derived root, then persists.
 *
 * Subsequent `unlock`: [verifyUnlock] finds the stored blob and attempts to decrypt it with the
 * candidate root. AEAD MAC failure → [VaultException.RecoveryFailed] (never silent).
 */
class PassphraseRecovery(
    /** Caller MUST wipe the CharArray after handing it to us; we never retain a reference beyond derivation. */
    private val passphrase: CharArray,
    private val identityHint: IdentityHint,
    private val params: Argon2Params = Argon2Params.V1,
    private val passwordHash: PasswordHash,
    private val keyDerivation: KeyDerivation,
    private val aead: AeadCipher,
    private val store: ValidationBlobStore,
) : RecoveryStrategy() {

    override suspend fun deriveRoot(): ByteArray {
        val salt = computeSalt()
        return passwordHash.deriveFromPassphrase(
            password = passphrase,
            salt = salt,
            memoryKib = params.memoryKiB,
            iterations = params.iterations,
            outputLength = RootKey.SIZE,
        )
    }

    override suspend fun verifyUnlock(candidateRoot: ByteArray) {
        val hintKey = hintKey(identityHint)
        val existing = store.read(hintKey)
        val aad = VALIDATION_AAD
        if (existing == null) {
            val sealed = aead.encrypt(VALIDATION_PLAINTEXT, candidateRoot, aad)
            store.write(hintKey, sealed.bytes)
            return
        }
        try {
            val opened = aead.decrypt(CryptoCiphertext(existing), candidateRoot, aad)
            if (!opened.contentEquals(VALIDATION_PLAINTEXT)) {
                throw VaultException.RecoveryFailed("Validation blob decrypted but plaintext mismatch")
            }
        } catch (e: CryptoException.DecryptionFailed) {
            throw VaultException.RecoveryFailed("Wrong passphrase (validation blob MAC failed)", e)
        } catch (e: CryptoException.MalformedCiphertext) {
            throw VaultException.RecoveryFailed("Validation blob malformed", e)
        }
    }

    private suspend fun computeSalt(): ByteArray = when (val h = identityHint) {
        is IdentityHint.GoogleAccount -> keyDerivation.derive(
            ikm = h.googleUid.encodeToByteArray(),
            salt = ByteArray(0),
            info = SALT_INFO,
            length = IdentityHint.SALT_SIZE,
        )
        is IdentityHint.NoGmsDevice -> h.deviceRandomSalt.copyOf()
    }

    companion object {
        private const val SALT_INFO = "salt-v1"

        /** Known plaintext sealed under the candidate root as the passphrase check. */
        internal val VALIDATION_PLAINTEXT: ByteArray = "vault-init-v1".encodeToByteArray()

        /** AAD for the validation blob — pinned and version-tagged. */
        internal val VALIDATION_AAD: ByteArray = "validation-blob-v1".encodeToByteArray()

        internal fun hintKey(hint: IdentityHint): String = when (hint) {
            is IdentityHint.GoogleAccount -> "ga:${hint.googleUid}"
            is IdentityHint.NoGmsDevice -> "nogms:${hint.deviceRandomSalt.toHex()}"
        }

        private fun ByteArray.toHex(): String =
            joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
