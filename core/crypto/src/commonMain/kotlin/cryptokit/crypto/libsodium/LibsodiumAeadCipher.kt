@file:OptIn(ExperimentalUnsignedTypes::class)

package cryptokit.crypto.libsodium

import com.ionspin.kotlin.crypto.aead.AeadCorrupedOrTamperedDataException
import com.ionspin.kotlin.crypto.aead.AuthenticatedEncryptionWithAssociatedData
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.RandomSource
import cryptokit.crypto.api.values.Ciphertext
import cryptokit.crypto.exception.CryptoException

/**
 * Real XChaCha20-Poly1305 IETF AEAD per FR-013 + Clarifications Q3.
 *
 * Wire layout returned in [Ciphertext]: `nonce(24) || ciphertext || mac(16)`.
 * Nonce is generated internally on every [encrypt] call — callers MUST NOT supply or
 * extract it (see [AeadCipher] KDoc).
 *
 * The optional [forcedNonceForTesting] constructor parameter is used **only** by
 * cross-platform vector parity tests (FR-022) to verify deterministic byte-equivalence
 * across platforms. It MUST be null in production.
 */
class LibsodiumAeadCipher(
    private val random: RandomSource = LibsodiumRandomSource(),
    private val forcedNonceForTesting: ByteArray? = null
) : AeadCipher {

    override suspend fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        aad: ByteArray
    ): Ciphertext {
        require(key.size == KEY_SIZE) { "Key must be $KEY_SIZE bytes, got ${key.size}" }
        LibsodiumInit.ensure()
        val nonce = forcedNonceForTesting?.copyOf()
            ?: try {
                LibsodiumRandom.buf(NONCE_SIZE).asByteArray()
            } catch (t: Throwable) {
                throw CryptoException.RandomSourceUnavailable(t)
            }
        require(nonce.size == NONCE_SIZE) { "Nonce must be $NONCE_SIZE bytes, got ${nonce.size}" }
        // ionspin AEAD API: xChaCha20Poly1305IetfEncrypt(message, additionalData, nonce, key)
        val ctTag = AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfEncrypt(
            plaintext.asUByteArray(),
            aad.asUByteArray(),
            nonce.asUByteArray(),
            key.asUByteArray()
        ).asByteArray()
        val out = ByteArray(NONCE_SIZE + ctTag.size)
        nonce.copyInto(out, 0)
        ctTag.copyInto(out, NONCE_SIZE)
        return Ciphertext(out)
    }

    override suspend fun decrypt(
        ciphertext: Ciphertext,
        key: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(key.size == KEY_SIZE) { "Key must be $KEY_SIZE bytes, got ${key.size}" }
        LibsodiumInit.ensure()
        val bytes = ciphertext.bytes
        if (bytes.size < NONCE_SIZE + MAC_SIZE) {
            throw CryptoException.MalformedCiphertext(
                "Ciphertext too short (${bytes.size} < ${NONCE_SIZE + MAC_SIZE})"
            )
        }
        val nonce = bytes.copyOfRange(0, NONCE_SIZE)
        val ctTag = bytes.copyOfRange(NONCE_SIZE, bytes.size)
        return try {
            AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfDecrypt(
                ctTag.asUByteArray(),
                aad.asUByteArray(),
                nonce.asUByteArray(),
                key.asUByteArray()
            ).asByteArray()
        } catch (e: AeadCorrupedOrTamperedDataException) {
            throw CryptoException.DecryptionFailed()
        }
    }

    companion object {
        const val KEY_SIZE = 32
        const val NONCE_SIZE = 24
        const val MAC_SIZE = 16
    }
}
