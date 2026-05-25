package com.launcher.adapters.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.launcher.api.crypto.AeadCipher
import com.launcher.api.crypto.CEK_SIZE
import com.launcher.api.crypto.POLY1305_MAC_SIZE
import com.launcher.api.crypto.ContentEncryptionKey
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.XCHACHA20_NONCE_SIZE
import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi

// XChaCha20-Poly1305 AEAD через libsodium. Combined-mode: auth tag prepended
// to ciphertext в одном байт-массиве (per libsodium API + crypto-envelope.md §Note on mac).
@OptIn(ExperimentalUuidApi::class)
class LibsodiumAeadCipher(
    private val sodium: LazySodiumAndroid = LibsodiumProvider.sodium,
) : AeadCipher {

    override fun encrypt(
        plaintext: ByteArray,
        key: ContentEncryptionKey,
        nonce: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(nonce.size == XCHACHA20_NONCE_SIZE)
        val keyBytes = key.bytesOrThrow()
        val cipherLen = LongArray(1)
        val out = ByteArray(plaintext.size + AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        val ok = sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            out,
            cipherLen,
            plaintext,
            plaintext.size.toLong(),
            aad,
            aad.size.toLong(),
            null,
            nonce,
            keyBytes,
        )
        check(ok) { "XChaCha20-Poly1305 encrypt failed" }
        // cipherLen[0] should == plaintext.size + ABYTES (16). out already correctly sized.
        return out
    }

    override fun decrypt(
        ciphertext: ByteArray,
        key: ContentEncryptionKey,
        nonce: ByteArray,
        aad: ByteArray,
    ): Outcome<ByteArray, CryptoError> {
        if (ciphertext.size < AEAD.XCHACHA20POLY1305_IETF_ABYTES) {
            return Outcome.Failure(CryptoError.MalformedEnvelope())
        }
        val keyBytes = key.bytesOrThrow()
        val out = ByteArray(ciphertext.size - AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        val outLen = LongArray(1)
        val ok = try {
            sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
                out,
                outLen,
                null,
                ciphertext,
                ciphertext.size.toLong(),
                aad,
                aad.size.toLong(),
                nonce,
                keyBytes,
            )
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.MacFailed())
        }
        return if (ok) Outcome.Success(out) else Outcome.Failure(CryptoError.MacFailed())
    }

    override fun randomNonce(): ByteArray = sodium.randomBytesBuf(XCHACHA20_NONCE_SIZE)

    override fun generateCEK(): ContentEncryptionKey =
        ContentEncryptionKey(sodium.randomBytesBuf(CEK_SIZE))
}
