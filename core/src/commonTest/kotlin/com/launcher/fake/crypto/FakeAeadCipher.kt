package com.launcher.fake.crypto

import com.launcher.api.crypto.AeadCipher
import com.launcher.api.crypto.CEK_SIZE
import com.launcher.api.crypto.ContentEncryptionKey
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.POLY1305_MAC_SIZE
import com.launcher.api.crypto.XCHACHA20_NONCE_SIZE
import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi

// WARNING: XOR-stub. НЕ криптография. Только для тестов portов.
// Имитирует AEAD интерфейс: encrypt возвращает ciphertext с приписанным
// синтетическим MAC; decrypt пересчитывает MAC и сравнивает.
@OptIn(ExperimentalUuidApi::class)
class FakeAeadCipher(private val seed: Int = 0x42) : AeadCipher {

    private var nonceCounter: Int = 0

    override fun encrypt(
        plaintext: ByteArray,
        key: ContentEncryptionKey,
        nonce: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(nonce.size == XCHACHA20_NONCE_SIZE)
        val keyBytes = key.bytesOrThrow()
        val ct = ByteArray(plaintext.size)
        for (i in plaintext.indices) {
            ct[i] = (plaintext[i].toInt() xor keyBytes[i % keyBytes.size].toInt() xor nonce[i % nonce.size].toInt()).toByte()
        }
        val tag = computeMac(ct, keyBytes, nonce, aad)
        // libsodium combined-mode: tag prepended to ciphertext
        return tag + ct
    }

    override fun decrypt(
        ciphertext: ByteArray,
        key: ContentEncryptionKey,
        nonce: ByteArray,
        aad: ByteArray,
    ): Outcome<ByteArray, CryptoError> {
        if (ciphertext.size < POLY1305_MAC_SIZE) {
            return Outcome.Failure(CryptoError.MalformedEnvelope())
        }
        val keyBytes = key.bytesOrThrow()
        val tag = ciphertext.copyOfRange(0, POLY1305_MAC_SIZE)
        val ct = ciphertext.copyOfRange(POLY1305_MAC_SIZE, ciphertext.size)
        val expected = computeMac(ct, keyBytes, nonce, aad)
        if (!tag.contentEquals(expected)) {
            return Outcome.Failure(CryptoError.MacFailed())
        }
        val pt = ByteArray(ct.size)
        for (i in ct.indices) {
            pt[i] = (ct[i].toInt() xor keyBytes[i % keyBytes.size].toInt() xor nonce[i % nonce.size].toInt()).toByte()
        }
        return Outcome.Success(pt)
    }

    override fun randomNonce(): ByteArray {
        val n = ByteArray(XCHACHA20_NONCE_SIZE)
        val v = ++nonceCounter
        n[0] = (v and 0xFF).toByte()
        n[1] = ((v ushr 8) and 0xFF).toByte()
        n[2] = ((v ushr 16) and 0xFF).toByte()
        n[3] = ((v ushr 24) and 0xFF).toByte()
        return n
    }

    override fun generateCEK(): ContentEncryptionKey {
        val b = ByteArray(CEK_SIZE)
        var x = seed
        for (i in 0 until CEK_SIZE) {
            x = (x * 1103515245 + 12345) and 0x7FFFFFFF
            b[i] = (x and 0xFF).toByte()
        }
        return ContentEncryptionKey(b)
    }

    // Deterministic synthetic MAC: XOR-fold over (ciphertext || nonce || aad) keyed by CEK.
    private fun computeMac(ct: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray {
        val mac = ByteArray(POLY1305_MAC_SIZE)
        fun mix(data: ByteArray) {
            for (i in data.indices) mac[i % POLY1305_MAC_SIZE] = (mac[i % POLY1305_MAC_SIZE].toInt() xor data[i].toInt()).toByte()
        }
        mix(ct); mix(nonce); mix(aad); mix(key)
        return mac
    }
}
