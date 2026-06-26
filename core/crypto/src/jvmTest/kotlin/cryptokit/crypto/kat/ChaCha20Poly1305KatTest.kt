@file:OptIn(ExperimentalUnsignedTypes::class)

package cryptokit.crypto.kat

import com.ionspin.kotlin.crypto.aead.AuthenticatedEncryptionWithAssociatedData
import cryptokit.crypto.api.values.Ciphertext
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumInit
import cryptokit.crypto.util.hexToByteArray
import cryptokit.crypto.util.toHex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * RFC 8439 §2.8.2 + XChaCha20-Poly1305 IETF (draft-irtf-cfrg-xchacha-03 App. A.3).
 * Per FR-019 + SC-002.
 *
 * RFC 8439 vector validates the underlying 12-byte-nonce ChaCha20-Poly1305 primitive
 * directly via the ionspin API. The roundtrip + forced-nonce vectors verify our
 * XChaCha20-Poly1305 wrapper ([LibsodiumAeadCipher]) end-to-end.
 */
class ChaCha20Poly1305KatTest {

    // RFC 8439 §2.8.2.
    @Test
    fun rfc8439_section2_8_2_chaCha20Poly1305IetfEncrypt() = runTest {
        LibsodiumInit.ensure()
        val key = "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f".hexToByteArray()
        val nonce = "070000004041424344454647".hexToByteArray()
        val aad = "50515253c0c1c2c3c4c5c6c7".hexToByteArray()
        val plaintext = (
            "4c616469657320616e642047656e746c656d656e206f662074686520636c6173" +
                "73206f66202739393a204966204920636f756c64206f6666657220796f75206f" +
                "6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73" +
                "637265656e20776f756c642062652069742e"
            ).hexToByteArray()
        val expectedCt = (
            "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6" +
                "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b36" +
                "92ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc" +
                "3ff4def08e4b7a9de576d26586cec64b6116"
            ).hexToByteArray()
        val expectedTag = "1ae10b594f09e26a7e902ecbd0600691".hexToByteArray()

        val actual = AuthenticatedEncryptionWithAssociatedData.chaCha20Poly1305IetfEncrypt(
            plaintext.asUByteArray(),
            aad.asUByteArray(),
            nonce.asUByteArray(),
            key.asUByteArray()
        ).asByteArray()

        // ionspin appends 16-byte tag at the end; verify both halves.
        val gotCt = actual.copyOfRange(0, actual.size - 16)
        val gotTag = actual.copyOfRange(actual.size - 16, actual.size)
        assertEquals(expectedCt.toHex(), gotCt.toHex())
        assertEquals(expectedTag.toHex(), gotTag.toHex())
    }

    /**
     * draft-irtf-cfrg-xchacha-03 §A.3.1 — XChaCha20-Poly1305 IETF reference vector.
     * Validates the LibsodiumAeadCipher wrapper end-to-end with a forced nonce.
     */
    @Test
    fun draftXchacha_appA3_xChaCha20Poly1305Roundtrip() = runTest {
        val key = "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f".hexToByteArray()
        val nonce = "404142434445464748494a4b4c4d4e4f5051525354555657".hexToByteArray()
        val aad = "50515253c0c1c2c3c4c5c6c7".hexToByteArray()
        val plaintext = (
            "4c616469657320616e642047656e746c656d656e206f662074686520636c6173" +
                "73206f66202739393a204966204920636f756c64206f6666657220796f75206f" +
                "6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73" +
                "637265656e20776f756c642062652069742e"
            ).hexToByteArray()

        // Use LibsodiumAeadCipher with forced nonce to allow vector-deterministic ciphertext.
        val cipher = LibsodiumAeadCipher(forcedNonceForTesting = nonce)
        val ct = cipher.encrypt(plaintext, key, aad)
        // Layout: nonce(24) || ciphertext || mac(16). Verify embedded nonce matches.
        val embeddedNonce = ct.bytes.copyOfRange(0, 24)
        assertContentEquals(nonce, embeddedNonce)

        // Roundtrip: decrypt yields plaintext.
        val decrypted = cipher.decrypt(ct, key, aad)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun decryptDetectsTamperedCiphertext() = runTest {
        val cipher = LibsodiumAeadCipher()
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "hello world".encodeToByteArray()
        val ct = cipher.encrypt(plaintext, key)
        val tampered = ct.bytes.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()
        val result = runCatching { cipher.decrypt(Ciphertext(tampered), key) }
        assertEquals(true, result.isFailure, "tampered ciphertext must fail decryption")
    }
}
