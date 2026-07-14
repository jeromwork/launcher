@file:OptIn(ExperimentalUnsignedTypes::class)

package cryptokit.crypto.libsodium

import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.box.BoxCorruptedOrTamperedDataException
import com.ionspin.kotlin.crypto.scalarmult.ScalarMultiplication
import com.ionspin.kotlin.crypto.signature.InvalidSignatureException
import com.ionspin.kotlin.crypto.signature.Signature as SodiumSignature
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.values.KeyPair
import cryptokit.crypto.api.values.SealedBlob
import cryptokit.crypto.api.values.SharedSecret
import cryptokit.crypto.api.values.Signature
import cryptokit.crypto.exception.CryptoException

/**
 * Real X25519 (ECDH via [ScalarMultiplication]) + Ed25519 ([SodiumSignature]) +
 * sealed-box envelope ([Box.seal] / [Box.sealOpen]) per FR-013.
 *
 * `deriveSharedSecret` returns the raw 32-byte X25519 output, matching RFC 7748 KAT
 * (NOT `crypto_box_beforenm`, which post-hashes the result).
 */
class LibsodiumAsymmetricCrypto : AsymmetricCrypto {

    override suspend fun generateX25519KeyPair(): KeyPair {
        LibsodiumInit.ensure()
        val kp = Box.keypair()
        return KeyPair(
            privateKey = kp.secretKey.asByteArray(),
            publicKey = kp.publicKey.asByteArray(),
            algorithm = "X25519"
        )
    }

    override suspend fun generateEd25519KeyPair(): KeyPair {
        LibsodiumInit.ensure()
        val kp = SodiumSignature.keypair()
        return KeyPair(
            privateKey = kp.secretKey.asByteArray(),
            publicKey = kp.publicKey.asByteArray(),
            algorithm = "Ed25519"
        )
    }

    override suspend fun ed25519KeyPairFromSeed(seed: ByteArray): KeyPair {
        require(seed.size == ED25519_SEED_SIZE) {
            "Ed25519 seed must be $ED25519_SEED_SIZE bytes, got ${seed.size}"
        }
        LibsodiumInit.ensure()
        val kp = SodiumSignature.seedKeypair(seed.asUByteArray())
        return KeyPair(
            privateKey = kp.secretKey.asByteArray(),
            publicKey = kp.publicKey.asByteArray(),
            algorithm = "Ed25519"
        )
    }

    override suspend fun deriveSharedSecret(
        myPrivate: ByteArray,
        theirPublic: ByteArray
    ): SharedSecret {
        require(myPrivate.size == X25519_KEY_SIZE) {
            "X25519 private key must be $X25519_KEY_SIZE bytes"
        }
        require(theirPublic.size == X25519_KEY_SIZE) {
            "X25519 public key must be $X25519_KEY_SIZE bytes"
        }
        LibsodiumInit.ensure()
        val bytes = try {
            ScalarMultiplication.scalarMultiplication(
                myPrivate.asUByteArray(),
                theirPublic.asUByteArray()
            ).asByteArray()
        } catch (t: Throwable) {
            throw CryptoException.InvalidPublicKey("X25519 scalarmult rejected key: ${t.message}")
        }
        // libsodium scalarmult returns all-zero on low-order points — treat as invalid.
        if (bytes.all { it == 0.toByte() }) {
            throw CryptoException.InvalidPublicKey("X25519 produced all-zero shared secret (low-order point)")
        }
        return SharedSecret(bytes)
    }

    override suspend fun sign(message: ByteArray, privateKey: ByteArray): Signature {
        require(privateKey.size == ED25519_SECRET_KEY_SIZE) {
            "Ed25519 secret key must be $ED25519_SECRET_KEY_SIZE bytes"
        }
        LibsodiumInit.ensure()
        val sig = SodiumSignature.detached(
            message.asUByteArray(),
            privateKey.asUByteArray()
        ).asByteArray()
        return Signature(sig)
    }

    override suspend fun verify(
        signature: Signature,
        message: ByteArray,
        publicKey: ByteArray
    ): Boolean {
        if (signature.bytes.size != ED25519_SIGNATURE_SIZE) return false
        if (publicKey.size != ED25519_PUBLIC_KEY_SIZE) return false
        LibsodiumInit.ensure()
        return try {
            SodiumSignature.verifyDetached(
                signature.bytes.asUByteArray(),
                message.asUByteArray(),
                publicKey.asUByteArray()
            )
            true
        } catch (e: InvalidSignatureException) {
            false
        }
    }

    override suspend fun sealForRecipient(
        payload: ByteArray,
        recipientPublicKey: ByteArray
    ): SealedBlob {
        require(recipientPublicKey.size == X25519_KEY_SIZE) {
            "Recipient X25519 public key must be $X25519_KEY_SIZE bytes"
        }
        LibsodiumInit.ensure()
        val sealed = Box.seal(
            payload.asUByteArray(),
            recipientPublicKey.asUByteArray()
        ).asByteArray()
        return SealedBlob(sealed)
    }

    override suspend fun openSealed(
        blob: SealedBlob,
        recipientPrivateKey: ByteArray
    ): ByteArray {
        require(recipientPrivateKey.size == X25519_KEY_SIZE) {
            "Recipient X25519 private key must be $X25519_KEY_SIZE bytes"
        }
        LibsodiumInit.ensure()
        // sealOpen needs (cipher, publicKey, secretKey) — derive pubkey from sk.
        val recipientPub = ScalarMultiplication.scalarMultiplicationBase(
            recipientPrivateKey.asUByteArray()
        )
        return try {
            Box.sealOpen(
                blob.bytes.asUByteArray(),
                recipientPub,
                recipientPrivateKey.asUByteArray()
            ).asByteArray()
        } catch (e: BoxCorruptedOrTamperedDataException) {
            throw CryptoException.DecryptionFailed("Sealed box MAC mismatch or wrong recipient")
        }
    }

    companion object {
        const val X25519_KEY_SIZE = 32
        const val ED25519_SECRET_KEY_SIZE = 64
        const val ED25519_PUBLIC_KEY_SIZE = 32
        const val ED25519_SIGNATURE_SIZE = 64
        const val ED25519_SEED_SIZE = 32
    }
}
