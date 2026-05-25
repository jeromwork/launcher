package com.launcher.adapters.crypto

import com.launcher.api.crypto.CIPHER_SUITE_ID_V1
import com.launcher.api.crypto.CEK_SIZE
import com.launcher.api.crypto.ContentEncryptionKey
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.ED25519_KEY_SIZE
import com.launcher.api.crypto.ED25519_SIGNATURE_SIZE
import com.launcher.api.crypto.HASH_OUTPUT_SIZE
import com.launcher.api.crypto.PublicKey
import com.launcher.api.crypto.SEALED_CEK_SIZE
import com.launcher.api.crypto.X25519_KEY_SIZE
import com.launcher.api.crypto.XCHACHA20_NONCE_SIZE
import com.launcher.api.result.Outcome
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// Phase 3 contract tests против libsodium. Гоняется на JVM через Robolectric —
// Lazysodium загружает libsodium native через JNA. Если CI host (Linux/macOS)
// не имеет libsodium installed — quickstart §3 говорит установить.
@RunWith(RobolectricTestRunner::class)
class LibsodiumAdaptersTest {

    @Test
    fun aead_encrypt_decrypt_roundtrip() {
        val aead = LibsodiumAeadCipher()
        val cek = aead.generateCEK()
        val nonce = aead.randomNonce()
        val plaintext = "Hello, libsodium AEAD".encodeToByteArray()
        val aad = byteArrayOf(0x01, 0x02, 0x03)
        val ct = aead.encrypt(plaintext, cek, nonce, aad)
        assertTrue(ct.size > plaintext.size)  // includes Poly1305 tag
        val cek2 = ContentEncryptionKey(cek.bytesOrThrow().copyOf())
        val result = aead.decrypt(ct, cek2, nonce, aad)
        assertTrue(result is Outcome.Success, "decrypt should succeed; got $result")
        assertContentEquals(plaintext, result.value)
    }

    @Test
    fun aead_decrypt_with_tampered_aad_fails_with_MacFailed() {
        val aead = LibsodiumAeadCipher()
        val cek = aead.generateCEK()
        val nonce = aead.randomNonce()
        val plaintext = byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        val ct = aead.encrypt(plaintext, cek, nonce, aad = byteArrayOf(0x01))
        val cek2 = ContentEncryptionKey(cek.bytesOrThrow().copyOf())
        val result = aead.decrypt(ct, cek2, nonce, aad = byteArrayOf(0x99.toByte()))
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is CryptoError.MacFailed)
    }

    @Test
    fun aead_decrypt_truncated_ciphertext_fails() {
        val aead = LibsodiumAeadCipher()
        val cek = aead.generateCEK()
        val result = aead.decrypt(byteArrayOf(0x01), cek, ByteArray(XCHACHA20_NONCE_SIZE), byteArrayOf())
        assertTrue(result is Outcome.Failure)
    }

    @Test
    fun aead_nonce_size_correct() {
        val aead = LibsodiumAeadCipher()
        assertEquals(XCHACHA20_NONCE_SIZE, aead.randomNonce().size)
    }

    @Test
    fun aead_cek_size_correct() {
        val aead = LibsodiumAeadCipher()
        val cek = aead.generateCEK()
        assertEquals(CEK_SIZE, cek.bytesOrThrow().size)
    }

    @Test
    fun asymm_sealCEK_unsealCEK_roundtrip() {
        val asymm = LibsodiumAsymmetricCrypto()
        val kp = asymm.generateX25519Pair("test-x25519")
        assertEquals(X25519_KEY_SIZE, kp.publicKey.bytes.size)
        val aead = LibsodiumAeadCipher()
        val cek = aead.generateCEK()
        val cekCopy = cek.bytesOrThrow().copyOf()
        val sealed = asymm.sealCEK(cek, kp.publicKey)
        assertEquals(SEALED_CEK_SIZE, sealed.size)
        val unsealed = asymm.unsealCEK(sealed, kp)
        assertTrue(unsealed is Outcome.Success, "unsealCEK failed: $unsealed")
        assertContentEquals(cekCopy, unsealed.value.bytesOrThrow())
    }

    @Test
    fun asymm_unsealCEK_with_wrong_key_fails() {
        val asymm = LibsodiumAsymmetricCrypto()
        val kp = asymm.generateX25519Pair("a")
        val other = asymm.generateX25519Pair("b")
        val aead = LibsodiumAeadCipher()
        val cek = aead.generateCEK()
        val sealed = asymm.sealCEK(cek, kp.publicKey)
        val result = asymm.unsealCEK(sealed, other)
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is CryptoError.MacFailed)
    }

    @Test
    fun sign_verify_roundtrip() {
        val sign = LibsodiumDigitalSignature()
        val kp = sign.generateEd25519Pair("test-ed25519")
        assertEquals(ED25519_KEY_SIZE, kp.publicKey.bytes.size)
        val data = "DeviceIdentity payload".encodeToByteArray()
        val sig = sign.sign(data, kp)
        assertEquals(ED25519_SIGNATURE_SIZE, sig.size)
        val verify = sign.verify(data, sig, kp.publicKey)
        assertTrue(verify is Outcome.Success)
    }

    @Test
    fun sign_verify_fails_on_tampered_data() {
        val sign = LibsodiumDigitalSignature()
        val kp = sign.generateEd25519Pair("a")
        val data = "Hello".encodeToByteArray()
        val sig = sign.sign(data, kp)
        val tampered = "Hellp".encodeToByteArray()
        val result = sign.verify(tampered, sig, kp.publicKey)
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is CryptoError.SignatureVerifyFailed)
    }

    @Test
    fun sign_verify_fails_on_wrong_pubkey() {
        val sign = LibsodiumDigitalSignature()
        val kp = sign.generateEd25519Pair("a")
        val other = sign.generateEd25519Pair("b")
        val data = "data".encodeToByteArray()
        val sig = sign.sign(data, kp)
        val result = sign.verify(data, sig, other.publicKey)
        assertTrue(result is Outcome.Failure)
    }

    @Test
    fun sign_verify_rejects_wrong_size_signature() {
        val sign = LibsodiumDigitalSignature()
        val kp = sign.generateEd25519Pair("a")
        val result = sign.verify(byteArrayOf(1, 2), ByteArray(32), kp.publicKey)
        assertTrue(result is Outcome.Failure)
    }

    @Test
    fun hash_returns_32_bytes_and_is_deterministic() {
        val hash = LibsodiumHashFunction()
        val a = hash.hash("test".encodeToByteArray())
        val b = hash.hash("test".encodeToByteArray())
        assertEquals(HASH_OUTPUT_SIZE, a.size)
        assertContentEquals(a, b)
    }

    @Test
    fun hash_differs_for_different_inputs() {
        val hash = LibsodiumHashFunction()
        val a = hash.hash("alice".encodeToByteArray())
        val b = hash.hash("bob".encodeToByteArray())
        assertNotEquals(a.toList(), b.toList())
    }

    // --- Контракт-парность с Fake: same port returns equivalent shapes ---

    @Test
    fun all_constants_match_libsodium() {
        // Verify size constants encoded в commonMain == libsodium runtime values.
        val asymm = LibsodiumAsymmetricCrypto()
        val kp = asymm.generateX25519Pair("size-check")
        assertEquals(X25519_KEY_SIZE, kp.publicKey.bytes.size)

        val sign = LibsodiumDigitalSignature()
        val skp = sign.generateEd25519Pair("size-check")
        assertEquals(ED25519_KEY_SIZE, skp.publicKey.bytes.size)
        assertEquals(ED25519_SIGNATURE_SIZE, sign.sign(byteArrayOf(1), skp).size)
    }
}
