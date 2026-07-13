package cryptokit.keys.api

import cryptokit.crypto.api.values.ByteArrayBase64Serializer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Wire-format для passphrase-wrapped root key (contracts/recovery-key-backup-v1.md §3).
 *
 * **Lifecycle**:
 *  • Setup: client generates root → user picks passphrase → derive wrapKey
 *    через Argon2id (memory-hard) → AEAD wrap root key → upload blob в Cloudflare Worker.
 *  • Recovery: client signs in same UID → fetch blob → prompt passphrase →
 *    derive wrapKey same params (params живут внутри blob'а) → AEAD unwrap.
 *
 * **Versioning** (per CLAUDE.md rule 5):
 *  • `schemaVersion` — для backward-compat reads.
 *  • `stableId` — required UUID v4 для provider-agnostic идентификации.
 *  • `salt` — 32 bytes raw (XChaCha20 standard).
 *  • `kdfParams` — Argon2id memoryKb/iterations/parallelism (см. [KdfParams]).
 *  • `ciphertext` — AEAD output ≥ 48 bytes.
 *  • `nonce` — 24 bytes raw.
 *  • `createdAt` — ISO-8601 Instant.
 */
@Serializable
data class RecoveryKeyBackupBlob(
    val schemaVersion: Int = SCHEMA_VERSION_V1,
    val stableId: StableId,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val salt: ByteArray,
    val kdfParams: KdfParams,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val ciphertext: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val nonce: ByteArray,
    val createdAt: Instant,
) {
    init {
        require(salt.size == 32) { "salt MUST be exactly 32 bytes (XChaCha20 spec)" }
        require(nonce.size == 24) { "nonce MUST be exactly 24 bytes (XChaCha20 nonce width)" }
        require(ciphertext.size >= 48) { "ciphertext MUST be >= 48 bytes (32 key + 16 Poly1305 tag)" }
        require(stableId.isNotEmpty()) { "stableId MUST be non-empty UUID v4" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecoveryKeyBackupBlob) return false
        return schemaVersion == other.schemaVersion &&
            stableId == other.stableId &&
            salt.contentEquals(other.salt) &&
            kdfParams == other.kdfParams &&
            ciphertext.contentEquals(other.ciphertext) &&
            nonce.contentEquals(other.nonce) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + stableId.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + kdfParams.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }

    companion object {
        const val SCHEMA_VERSION_V1: Int = 1
        const val SCHEMA_VERSION: Int = SCHEMA_VERSION_V1
    }
}
