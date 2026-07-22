package com.launcher.adapters.crypto

import family.wire.ByteArrayBase64Serializer
import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * On-disk wire format for wrapped private key bytes — the persistence twin of the crypto
 * value `family.crypto.api.values.WrappedKeyMaterial`.
 *
 * TASK-141: this type carries the version header + @Serializable that `:core:crypto` may
 * not (rule 1 crypto exception). It lives in the adapter layer (`FileKeyBlobStore`), which
 * maps it to/from `WrappedKeyMaterial`. Crypto never sees this shape.
 *
 * Version header (`docs/architecture/wire-format.md` §3, invariant I1): the three fields carry
 * a dotted [WireVersion] each, serialized as a string (`"1.0"`). A local-only file with a single
 * reader (`FileKeyBlobStore` on the same device) still gets the three-field header so it obeys the
 * same reader gate as every other format — the on-disk shape does not get to be special.
 *
 * Layout (per contracts/key-blob-v1.md, spec 016):
 *  • [schemaVersion] / [minReaderVersion] / [minWriterVersion] — version header.
 *  • [wrappedKey] — AES-GCM-wrapped raw X25519/Ed25519 private key bytes.
 *  • [iv] — 12-byte IV used to wrap.
 *  • [wrapKeyAlias] — Android Keystore alias of the wrap AES key.
 *  • [retiredAt] / [replacedBy] — set when the key is rotated out (future rotation spec, TBD).
 *
 * `toString()` MUST NOT log raw [wrappedKey] / [iv] bytes (only sizes).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("KeyBlob")
class KeyBlob(
    // @EncodeDefault: FileKeyBlobStore encodes with encodeDefaults=true today, but I1 requires the
    // version header on every document regardless of the encoder's default handling.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val algorithm: String,
    val createdAt: Instant,
    val retiredAt: Instant? = null,
    val replacedBy: String? = null,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val wrappedKey: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val iv: ByteArray,
    val wrapKeyAlias: String
) : WireVersionHeader {
    override fun toString(): String =
        "KeyBlob(schemaVersion=$schemaVersion, algorithm=$algorithm, createdAt=$createdAt, " +
            "retiredAt=$retiredAt, replacedBy=$replacedBy, wrappedKey=<${wrappedKey.size} bytes>, " +
            "iv=<${iv.size} bytes>, wrapKeyAlias=$wrapKeyAlias)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyBlob) return false
        if (schemaVersion != other.schemaVersion) return false
        if (minReaderVersion != other.minReaderVersion) return false
        if (minWriterVersion != other.minWriterVersion) return false
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
        var result = schemaVersion.hashCode()
        result = 31 * result + minReaderVersion.hashCode()
        result = 31 * result + minWriterVersion.hashCode()
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
        /** Version constants (`docs/architecture/wire-format.md` §11 — one named constant per format). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** A KeyBlob is local to one device and never read by another build. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** Written wholesale by the device that owns it; no cross-writer merge exists. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}
