package family.crypto.api.values

/**
 * Asymmetric key pair (X25519 or Ed25519). Per data-model.md §"Cryptographic value types".
 *
 * `toString()` MUST NOT expose [privateKey]. `equals`/`hashCode` based on [publicKey] +
 * [algorithm] only — at rotation time [privateKey] differs but identity persists by pub key.
 */
class KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
    val algorithm: String
) {
    override fun toString(): String =
        "KeyPair(algorithm=$algorithm, publicKey=<${publicKey.size} bytes>, privateKey=<REDACTED>)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyPair) return false
        if (algorithm != other.algorithm) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = 31 * algorithm.hashCode() + publicKey.contentHashCode()
}
