package family.keys.api

import family.keys.api.internal.ByteArrayBase64Serializer
import kotlinx.serialization.Serializable

/**
 * Hybrid-encrypted envelope per [specs/011-contacts-and-e2e-encrypted-media §C-3]:
 *  1. A per-blob random Content Encryption Key (CEK) — 32 bytes — encrypts [ciphertext]
 *     via XChaCha20-Poly1305 (AEAD).
 *  2. CEK is independently encrypted under each recipient's X25519 public key via
 *     libsodium `crypto_box_seal`; the per-recipient blob lives in [recipientKeys].
 *  3. The CEK is wiped after seal; only ciphertext + the per-recipient sealed copies
 *     remain.
 *
 * Reading: a recipient looks up their own entry in [recipientKeys] by their
 * [DeviceId], opens it with their X25519 private key, recovers the CEK, then decrypts
 * [ciphertext]. Recipients not in the map cannot read; `crypto_box_seal` is
 * anonymous, so absence of a key cannot be guessed by analyzing the blob.
 *
 * **Wire-format invariants** (CLAUDE.md rule 5):
 *  - [schemaVersion] is monotonic. Future bumps for algorithm changes.
 *  - [algorithm] = `"envelope-xchacha20poly1305-x25519-v1"`. New strings for new schemes.
 *  - [aad] is recomputed by the reader from `(namespace, key, schemaVersion)` and
 *    compared against the stored value to detect context confusion.
 *  - [recipientKeys] is a map; key is [DeviceId.value], value is the sealed CEK
 *    (48 + 32 = 80 bytes per recipient: ephemeralPub(32) + ciphertext(32) + mac(16)).
 *
 * **Multi-recipient = list with N entries**. F-5b MVP uses N=1..M depending on the
 * owner's device count plus delegated helpers; the wire format does not change as
 * recipients grow.
 */
@Serializable
data class Envelope(
    val schemaVersion: Int = SCHEMA_VERSION,
    val algorithm: String = ALGORITHM_V1,

    @Serializable(with = ByteArrayBase64Serializer::class)
    val ciphertext: ByteArray,

    @Serializable(with = ByteArrayBase64Serializer::class)
    val nonce: ByteArray,

    @Serializable(with = ByteArrayBase64Serializer::class)
    val aad: ByteArray,

    /** DeviceId.value → Base64-encoded sealed CEK bytes. */
    val recipientKeys: Map<String, @Serializable(with = ByteArrayBase64Serializer::class) ByteArray>
) {
    init {
        require(schemaVersion >= 1) { "schemaVersion must be >= 1" }
        require(algorithm.isNotEmpty()) { "algorithm must not be empty" }
        require(nonce.size == NONCE_SIZE) { "nonce size ${nonce.size} != $NONCE_SIZE" }
        require(recipientKeys.isNotEmpty()) { "at least one recipient required" }
        recipientKeys.forEach { (id, blob) ->
            require(id.isNotEmpty()) { "recipient deviceId must not be empty" }
            require(blob.size == SEALED_CEK_SIZE) {
                "sealed CEK size ${blob.size} != $SEALED_CEK_SIZE for $id"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Envelope) return false
        if (schemaVersion != other.schemaVersion) return false
        if (algorithm != other.algorithm) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!aad.contentEquals(other.aad)) return false
        if (recipientKeys.size != other.recipientKeys.size) return false
        for ((id, blob) in recipientKeys) {
            val theirs = other.recipientKeys[id] ?: return false
            if (!blob.contentEquals(theirs)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var r = schemaVersion
        r = 31 * r + algorithm.hashCode()
        r = 31 * r + ciphertext.contentHashCode()
        r = 31 * r + nonce.contentHashCode()
        r = 31 * r + aad.contentHashCode()
        r = 31 * r + recipientKeys.entries.sumOf {
            31 * it.key.hashCode() + it.value.contentHashCode()
        }
        return r
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1
        const val ALGORITHM_V1: String = "envelope-xchacha20poly1305-x25519-v1"

        /** XChaCha20-Poly1305 nonce size (24 bytes). */
        const val NONCE_SIZE: Int = 24

        /** libsodium sealed-box output for 32-byte CEK = ephemeralPub(32) + ct(32) + mac(16) = 80. */
        const val SEALED_CEK_SIZE: Int = 80

        /** CEK is always 32 bytes (XChaCha20-Poly1305 key size). */
        const val CEK_SIZE: Int = 32
    }
}
