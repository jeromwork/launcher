package com.launcher.adapters.crypto

import family.crypto.api.values.ByteArrayBase64Serializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * On-disk wire format for wrapped private key bytes — the persistence twin of the crypto
 * value `family.crypto.api.values.WrappedKeyMaterial`.
 *
 * TASK-141: this type carries the schema version + @Serializable that `:core:crypto` may
 * not (rule 1 crypto exception). It lives in the adapter layer (`FileKeyBlobStore`), which
 * maps it to/from `WrappedKeyMaterial`. Crypto never sees this shape.
 *
 * Layout (per contracts/key-blob-v1.md, spec 016):
 *  • [schemaVersion] — wire-format version. Current = [CURRENT_SCHEMA_VERSION].
 *  • [wrappedKey] — AES-GCM-wrapped raw X25519/Ed25519 private key bytes.
 *  • [iv] — 12-byte IV used to wrap.
 *  • [wrapKeyAlias] — Android Keystore alias of the wrap AES key.
 *  • [retiredAt] / [replacedBy] — set when the key is rotated out (future rotation spec, TBD).
 *
 * `toString()` MUST NOT log raw [wrappedKey] / [iv] bytes (only sizes).
 */
@Serializable
@SerialName("KeyBlob")
class KeyBlob(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val algorithm: String,
    val createdAt: Instant,
    val retiredAt: Instant? = null,
    val replacedBy: String? = null,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val wrappedKey: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val iv: ByteArray,
    val wrapKeyAlias: String
) {
    override fun toString(): String =
        "KeyBlob(schemaVersion=$schemaVersion, algorithm=$algorithm, createdAt=$createdAt, " +
            "retiredAt=$retiredAt, replacedBy=$replacedBy, wrappedKey=<${wrappedKey.size} bytes>, " +
            "iv=<${iv.size} bytes>, wrapKeyAlias=$wrapKeyAlias)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyBlob) return false
        if (schemaVersion != other.schemaVersion) return false
        if (algorithm != other.algorithm) return false
        if (createdAt != other.createdAt) return false
        if (retiredAt != other.retiredAt) return false
        if (replacedBy != other.replacedBy) return false
        if (!wrappedKey.contentEquals(other.wrappedKey)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (wrapKeyAlias != other.wrapKeyAlias) return false
        return true
    }

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (retiredAt?.hashCode() ?: 0)
        result = 31 * result + (replacedBy?.hashCode() ?: 0)
        result = 31 * result + wrappedKey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + wrapKeyAlias.hashCode()
        return result
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
