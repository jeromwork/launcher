package family.keys.api

import kotlinx.datetime.Instant

/**
 * Passphrase-wrapped root key (contracts/recovery-key-backup-v1.md §3).
 *
 * **Lifecycle**:
 *  • Setup: client generates root → user picks passphrase → derive wrapKey
 *    через Argon2id (memory-hard) → AEAD wrap root key → upload blob в Cloudflare Worker.
 *  • Recovery: client signs in same UID → fetch blob → prompt passphrase →
 *    derive wrapKey same params (params живут внутри blob'а) → AEAD unwrap.
 *
 * **Pure crypto type** (`:core:keys`, TASK-141): carries no schema version and no
 * serialization annotations (CLAUDE.md rule 1 crypto exception). The wire version +
 * JSON serialization live above crypto, in the adapter that stores this blob — the
 * `@Serializable RecoveryKeyBackupBlobDto` + `RecoveryBlobJsonCodec` in
 * `com.launcher.app.data.recovery` (:app) own the `schemaVersion` and the reader
 * gate. Fields:
 *  • `stableId` — required UUID v4 для provider-agnostic идентификации.
 *  • `salt` — 32 bytes raw (XChaCha20 standard).
 *  • `kdfParams` — Argon2id memoryKb/iterations/parallelism (см. [KdfParams]).
 *  • `ciphertext` — AEAD output ≥ 48 bytes.
 *  • `nonce` — 24 bytes raw.
 *  • `createdAt` — ISO-8601 Instant.
 */
class RecoveryKeyBackupBlob(
    val stableId: StableId,
    val salt: ByteArray,
    val kdfParams: KdfParams,
    val ciphertext: ByteArray,
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
        return stableId == other.stableId &&
            salt.contentEquals(other.salt) &&
            kdfParams == other.kdfParams &&
            ciphertext.contentEquals(other.ciphertext) &&
            nonce.contentEquals(other.nonce) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = stableId.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + kdfParams.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
