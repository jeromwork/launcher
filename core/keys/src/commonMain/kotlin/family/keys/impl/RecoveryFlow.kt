package family.keys.impl

import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.RandomSource
import cryptokit.crypto.api.values.Ciphertext
import cryptokit.crypto.exception.CryptoException
import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.api.PassphraseAttemptCounter
import family.keys.api.KdfParams
import family.keys.api.PassphrasePrompter
import family.keys.api.RecoveryError
import family.keys.api.RecoveryKeyBackup
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.api.RootKey
import family.keys.api.BackupError
import kotlinx.datetime.Clock

/**
 * Setup + Recovery flows для F-5 root key (T063-T065, FR-003, FR-027).
 *
 * **Setup flow** ([performSetup]): после генерации root key (через
 * [RootKeyManagerImpl.getOrCreate]) — запросить passphrase у user'а →
 * derive Argon2id wrapKey → AEAD-wrap root key → upload blob в
 * [RecoveryKeyBackup].
 *
 * **Recovery flow** ([performRecovery]): на новом устройстве после Sign-In —
 * fetchBlob → prompt passphrase → derive wrapKey → AEAD-unwrap root → seed
 * RootKeyManager local cache + persistent SecureKeyStore через
 * `RootKeyManagerImpl.seedFromRecovery`.
 *
 * **Attempt counter** (H-1): persistent counter blocks user после 3 fail подряд;
 * после `resetTimeout` (default 1 час) counter сбрасывается.
 *
 * **Memory hygiene**:
 *  • Passphrase CharArray обнуляется сразу после Argon2id derivation.
 *  • Derived 32-byte wrapKey ByteArray обнуляется после wrap/unwrap (G-1).
 *  • Plaintext root key 32-byte buffer обнуляется после use.
 */
class RecoveryFlow(
    private val rootKeyManager: RootKeyManagerImpl,
    private val backup: RecoveryKeyBackup,
    private val kdf: Argon2idPassphraseKdf,
    private val aead: AeadCipher,
    private val random: RandomSource,
    private val prompter: PassphrasePrompter,
    private val attemptCounter: PassphraseAttemptCounter,
    private val clock: Clock = Clock.System,
    /** KDF params для setup. Default = interactive из spec 018 FR-030. */
    private val setupKdfParams: KdfParams = KdfParams()
) {

    /**
     * Создаёт RecoveryKeyBackupBlob и upload'ит в Backup. Вызывается сразу после
     * генерации root key через [RootKeyManagerImpl.getOrCreate].
     *
     * Идемпотентно: повторный setup для same UID — overwrite blob (rotation
     * passphrase).
     */
    suspend fun performSetup(
        identity: AuthIdentity,
        rootKey: RootKey
    ): Outcome<Unit, RecoveryError> {
        val passphrase = when (val r = prompter.requestSetupPassphrase()) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(r.error)
        }
        val salt = random.nextBytes(32)
        val params = setupKdfParams
        var wrapKey: ByteArray? = null
        try {
            if (passphrase.size < 8) {
                return Outcome.Failure(RecoveryError.Cancelled)
            }
            wrapKey = kdf.derive(passphrase, salt, identity.stableId, params)
            // AAD = "f5-recovery-vault-v1" — domain-separated от ConfigCipher AAD.
            val aad = AAD_PREFIX.encodeToByteArray()
            val ct: Ciphertext = aead.encrypt(rootKey.bytes, wrapKey, aad)
            val nonce = ct.bytes.copyOfRange(0, 24)
            val cipherPart = ct.bytes.copyOfRange(24, ct.bytes.size)
            val blob = RecoveryKeyBackupBlob(
                stableId = identity.stableId,
                salt = salt,
                kdfParams = params,
                ciphertext = cipherPart,
                nonce = nonce,
                createdAt = clock.now()
            )
            return when (val s = backup.uploadBlob(identity.stableId, blob)) {
                is Outcome.Success -> Outcome.Success(Unit)
                is Outcome.Failure -> Outcome.Failure(RecoveryError.MalformedVault) // upstream error surfaced as broad
            }
        } finally {
            passphrase.fill(' ')
            wrapKey?.fill(0)
        }
    }

    /**
     * Восстанавливает root key на новом устройстве. Предполагает, что
     * `RootKeyManagerImpl.getOrCreate` ранее вернул `RecoveryRequired`
     * (Keystore пуст для этой identity).
     *
     * Step 1: fetchBlob. Если NotFound → NoVaultPresent.
     * Step 2: check attempt counter (persistent, H-1). Если ≥ maxAttempts → TooManyAttempts.
     * Step 3: prompt passphrase.
     * Step 4: derive wrapKey + AEAD-unwrap. AeadAuthFailed → WrongPassphrase + record attempt.
     * Step 5: seed RootKeyManager → return Success.
     */
    suspend fun performRecovery(identity: AuthIdentity): Outcome<RootKey, RecoveryError> {
        require(identity.stableId.isNotEmpty())

        val blob = when (val fetch = backup.fetchBlob(identity.stableId)) {
            is Outcome.Success -> fetch.value
            is Outcome.Failure -> return when (fetch.error) {
                BackupError.NotFound -> Outcome.Failure(RecoveryError.NoVaultPresent)
                BackupError.Malformed -> Outcome.Failure(RecoveryError.MalformedVault)
                is BackupError.UnsupportedSchema -> Outcome.Failure(RecoveryError.MalformedVault)
                BackupError.SchemaDowngradeDetected -> Outcome.Failure(RecoveryError.MalformedVault)
                else -> Outcome.Failure(RecoveryError.NoVaultPresent)
            }
        }

        // Schema-version validation (H-3 analog для recovery blob).
        if (blob.schemaVersion > RecoveryKeyBackupBlob.SCHEMA_VERSION) {
            return Outcome.Failure(RecoveryError.MalformedVault)
        }

        // Attempt counter check (H-1).
        attemptCounter.resetIfExpired(identity.stableId)
        if (attemptCounter.currentCount(identity.stableId) >= attemptCounter.maxAttempts) {
            return Outcome.Failure(RecoveryError.TooManyAttempts)
        }

        val passphrase = when (val r = prompter.requestRecoveryPassphrase()) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(r.error)
        }

        var wrapKey: ByteArray? = null
        var plaintext: ByteArray? = null
        try {
            wrapKey = kdf.derive(passphrase, blob.salt, identity.stableId, blob.kdfParams)
            val aad = AAD_PREFIX.encodeToByteArray()
            val envelope = blob.nonce + blob.ciphertext
            plaintext = try {
                aead.decrypt(Ciphertext(envelope), wrapKey, aad)
            } catch (e: CryptoException.DecryptionFailed) {
                val newCount = attemptCounter.recordFailedAttempt(identity.stableId)
                return if (newCount >= attemptCounter.maxAttempts) {
                    Outcome.Failure(RecoveryError.TooManyAttempts)
                } else {
                    Outcome.Failure(RecoveryError.WrongPassphrase)
                }
            } catch (e: CryptoException.MalformedCiphertext) {
                return Outcome.Failure(RecoveryError.MalformedVault)
            }
            if (plaintext.size != RootKey.SIZE) {
                return Outcome.Failure(RecoveryError.MalformedVault)
            }
            // Seed local SecureKeyStore + cache.
            rootKeyManager.seedFromRecovery(identity, plaintext)
            attemptCounter.clear(identity.stableId)
            return Outcome.Success(RootKey(plaintext.copyOf()))
        } finally {
            passphrase.fill(' ')
            wrapKey?.fill(0)
            plaintext?.fill(0)
        }
    }

    companion object {
        /**
         * AAD bytes mixed into AEAD computation. Wire-format constant — renaming would
         * change ciphertext bytes for any blob produced before/after the rename, breaking
         * roundtrip even within v1. Legacy name retained per inventory.md §D4 scope-exclusion.
         */
        const val AAD_PREFIX: String = "f5-recovery-vault-v1"
    }
}
