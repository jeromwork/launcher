package family.keys.api

import family.keys.api.internal.ByteArrayBase64Serializer
import kotlinx.serialization.Serializable

/**
 * Wire-format для passphrase-wrapped root key, хранимого в Firestore по пути
 * `users/{uid}/recovery-key` (spec 018 FR-009, FR-010).
 *
 * **Lifecycle**:
 *  • Setup: client generates root → user picks passphrase → derive wrapKey
 *    через Argon2id (memory-hard) → AEAD wrap root key → upload blob в Firestore.
 *  • Recovery: client signs in same UID → fetch blob → prompt passphrase →
 *    derive wrapKey same params (params живут внутри blob'а) → AEAD unwrap.
 *
 * **Versioning** (per CLAUDE.md rule 5):
 *  • `schemaVersion` — для backward-compat reads.
 *  • `algorithm` — `"argon2id-xchacha20poly1305-v1"` в v1; позволяет смену primitives
 *    без breaking schemaVersion.
 *  • `kdfParams` — Argon2id memory/iterations/parallelism (см. [PassphraseKdfParams]).
 *    Хранятся IN blob'е (не глобальные константы) чтобы tuning params в будущем
 *    не сломал старые vault'ы.
 *
 * **App-agnostic** (FR-022): blob НЕ содержит app package name, build version,
 * device identifiers — только криптографические primitives. Это enables future
 * multi-app cohabitation (S-2) без cross-app vault leak'ов.
 */
@Serializable
data class RecoveryKeyBackupBlob(
    val schemaVersion: Int = SCHEMA_VERSION,
    val algorithm: String = ALGORITHM_V1,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val kdfSalt: ByteArray,
    val kdfParams: PassphraseKdfParams = PassphraseKdfParams(),
    @Serializable(with = ByteArrayBase64Serializer::class)
    val wrappedRootKey: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val nonce: ByteArray,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecoveryKeyBackupBlob) return false
        return schemaVersion == other.schemaVersion &&
            algorithm == other.algorithm &&
            kdfSalt.contentEquals(other.kdfSalt) &&
            kdfParams == other.kdfParams &&
            wrappedRootKey.contentEquals(other.wrappedRootKey) &&
            nonce.contentEquals(other.nonce) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + kdfSalt.contentHashCode()
        result = 31 * result + kdfParams.hashCode()
        result = 31 * result + wrappedRootKey.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }

    companion object {
        /** Wire format version (contracts/recovery-key-backup-v1.md §1). */
        const val SCHEMA_VERSION_V1: Int = 1
        /** Alias for backward compatibility with RecoveryBlobCodec. */
        const val SCHEMA_VERSION: Int = SCHEMA_VERSION_V1
        const val ALGORITHM_V1: String = "argon2id-xchacha20poly1305-v1"
    }
}
