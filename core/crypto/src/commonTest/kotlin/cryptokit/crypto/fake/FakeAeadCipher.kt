package cryptokit.crypto.fake

import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.RandomSource
import cryptokit.crypto.api.values.Ciphertext
import cryptokit.crypto.exception.CryptoException

/**
 * TEST-ONLY [AeadCipher]. NOT real cryptography — uses XOR with key and tracks (key, nonce)
 * pairs to surface nonce reuse. Wire-format: `nonce(24) || ciphertext || mac(16)` where:
 *  • ciphertext = plaintext XOR keystream (repeating key XOR nonce).
 *  • mac = XOR-fold of ciphertext + aad (deterministic, integrity-ish).
 *
 * Per FR-017 — fake adapters are sufficient for unit tests of consumers without pulling in
 * libsodium native libs.
 */
class FakeAeadCipher(
    private val random: RandomSource = FakeRandomSource()
) : AeadCipher {

    private val noncesByKey = mutableMapOf<String, MutableSet<String>>()

    override suspend fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        aad: ByteArray
    ): Ciphertext {
        require(key.size == 32) { "FakeAeadCipher requires 32-byte key, got ${key.size}" }
        val nonce = random.nextBytes(NONCE_SIZE)
        trackNonce(key, nonce)

        val ct = ByteArray(plaintext.size)
        for (i in plaintext.indices) {
            ct[i] = (plaintext[i].toInt() xor key[i % key.size].toInt() xor nonce[i % nonce.size].toInt()).toByte()
        }
        val mac = computeMac(ct, aad, key)

        val out = ByteArray(NONCE_SIZE + ct.size + MAC_SIZE)
        nonce.copyInto(out, 0)
        ct.copyInto(out, NONCE_SIZE)
        mac.copyInto(out, NONCE_SIZE + ct.size)
        return Ciphertext(out)
    }

    override suspend fun decrypt(
        ciphertext: Ciphertext,
        key: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(key.size == 32) { "FakeAeadCipher requires 32-byte key, got ${key.size}" }
        val bytes = ciphertext.bytes
        if (bytes.size < NONCE_SIZE + MAC_SIZE) {
            throw CryptoException.MalformedCiphertext(
                "Ciphertext shorter than nonce+mac (${bytes.size} < ${NONCE_SIZE + MAC_SIZE})"
            )
        }
        val nonce = bytes.copyOfRange(0, NONCE_SIZE)
        val ct = bytes.copyOfRange(NONCE_SIZE, bytes.size - MAC_SIZE)
        val mac = bytes.copyOfRange(bytes.size - MAC_SIZE, bytes.size)

        val expectedMac = computeMac(ct, aad, key)
        if (!mac.contentEquals(expectedMac)) {
            throw CryptoException.DecryptionFailed()
        }

        val pt = ByteArray(ct.size)
        for (i in ct.indices) {
            pt[i] = (ct[i].toInt() xor key[i % key.size].toInt() xor nonce[i % nonce.size].toInt()).toByte()
        }
        return pt
    }

    private fun trackNonce(key: ByteArray, nonce: ByteArray) {
        val keyHex = key.toHex()
        val nonceHex = nonce.toHex()
        val seen = noncesByKey.getOrPut(keyHex) { mutableSetOf() }
        if (!seen.add(nonceHex)) {
            throw CryptoException.NonceReuseDetected(
                "Nonce reuse detected for key=$keyHex nonce=$nonceHex"
            )
        }
    }

    private fun computeMac(ct: ByteArray, aad: ByteArray, key: ByteArray): ByteArray {
        val mac = ByteArray(MAC_SIZE)
        for (i in ct.indices) {
            mac[i % MAC_SIZE] = (mac[i % MAC_SIZE].toInt() xor ct[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        for (i in aad.indices) {
            mac[i % MAC_SIZE] = (mac[i % MAC_SIZE].toInt() xor aad[i].toInt()).toByte()
        }
        return mac
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    companion object {
        const val NONCE_SIZE = 24
        const val MAC_SIZE = 16
    }
}
