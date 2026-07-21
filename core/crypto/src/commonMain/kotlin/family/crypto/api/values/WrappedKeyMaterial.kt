package family.crypto.api.values

import kotlinx.datetime.Instant

/**
 * The output of the TEE wrap step, handed between [family.crypto.api.SecureKeyStore]
 * and a [family.crypto.api.KeyBlobStore] persistence adapter.
 *
 * Pure crypto value (`:core:crypto`, TASK-141): no schema version, no serialization
 * annotations (rule 1 crypto exception). The persistence adapter above crypto adds the
 * wire version and serializes this as a `KeyBlob`; crypto only ever sees already-wrapped
 * opaque bytes and never learns the on-disk format.
 *
 *  • [algorithm] — logical key algorithm ("X25519" / "Ed25519" / "RAW"), diagnostic.
 *  • [createdAt] — when the secret was wrapped.
 *  • [wrappedKey] — AES-GCM-wrapped raw private key bytes.
 *  • [iv] — 12-byte GCM IV used to wrap.
 *  • [wrapKeyAlias] — Android Keystore alias of the wrap AES key.
 *
 * `toString()` MUST NOT log raw [wrappedKey] / [iv] bytes (only sizes).
 */
class WrappedKeyMaterial(
    val algorithm: String,
    val createdAt: Instant,
    val wrappedKey: ByteArray,
    val iv: ByteArray,
    val wrapKeyAlias: String,
) {
    override fun toString(): String =
        "WrappedKeyMaterial(algorithm=$algorithm, createdAt=$createdAt, " +
            "wrappedKey=<${wrappedKey.size} bytes>, iv=<${iv.size} bytes>, wrapKeyAlias=$wrapKeyAlias)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WrappedKeyMaterial) return false
        return algorithm == other.algorithm &&
            createdAt == other.createdAt &&
            wrappedKey.contentEquals(other.wrappedKey) &&
            iv.contentEquals(other.iv) &&
            wrapKeyAlias == other.wrapKeyAlias
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + wrappedKey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + wrapKeyAlias.hashCode()
        return result
    }
}
