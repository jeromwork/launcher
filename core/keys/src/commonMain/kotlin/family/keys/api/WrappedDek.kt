package family.keys.api

import family.keys.api.internal.ByteArrayBase64Serializer
import kotlinx.serialization.Serializable

/**
 * Wire-format для DEK (Data Encryption Key), завёрнутого root key (FR-004, FR-005).
 *
 * Lifecycle: каждый DEK identified by stable [name] (например, `"config-cipher-aead-v1"`,
 * `"pair-x25519-v1"`, `"photo-aead-v1"`). DEK = 32-байтовый key material, зашифрованный
 * под root key через AEAD.
 *
 * Storage layer (KeyRegistry) хранит map `name → WrappedDek`. Forward-compat invariant
 * (FR-005): старый client читая storage с DEK от будущего spec'а (unknown name) — игнорирует,
 * не падает.
 */
@Serializable
data class WrappedDek(
    val name: String,
    val schemaVersion: Int = SCHEMA_VERSION,
    val algorithm: String = ALGORITHM_V1,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val ciphertext: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val nonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WrappedDek) return false
        return name == other.name &&
            schemaVersion == other.schemaVersion &&
            algorithm == other.algorithm &&
            ciphertext.contentEquals(other.ciphertext) &&
            nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + schemaVersion
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1
        const val ALGORITHM_V1: String = "xchacha20poly1305-v1"
    }
}
