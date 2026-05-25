package com.launcher.fake.crypto

import com.launcher.api.crypto.AsymmetricCrypto
import com.launcher.api.crypto.CEK_SIZE
import com.launcher.api.crypto.ContentEncryptionKey
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceKeyPair
import com.launcher.api.crypto.POLY1305_MAC_SIZE
import com.launcher.api.crypto.PublicKey
import com.launcher.api.crypto.SEALED_CEK_SIZE
import com.launcher.api.crypto.InMemoryPrivateKey
import com.launcher.api.crypto.X25519_KEY_SIZE
import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FakeAsymmetricCrypto : AsymmetricCrypto {

    override fun generateX25519Pair(alias: String): DeviceKeyPair {
        // Deterministic от alias — стабильные тесты.
        val priv = ByteArray(X25519_KEY_SIZE)
        val pub = ByteArray(X25519_KEY_SIZE)
        val seed = alias.hashCode()
        var x = seed
        for (i in 0 until X25519_KEY_SIZE) {
            x = (x * 1103515245 + 12345) and 0x7FFFFFFF
            priv[i] = (x and 0xFF).toByte()
            pub[i] = ((x ushr 8) and 0xFF).toByte()
        }
        return DeviceKeyPair(PublicKey(pub), InMemoryPrivateKey(alias, priv))
    }

    // sealedCEK layout (80 bytes): ephemeral_pub(32) || encrypted_cek(32) || mac(16).
    // encrypted_cek = CEK XOR recipientPub. mac = XOR-fold over (ephemeral_pub || encrypted_cek || recipientPub).
    override fun sealCEK(cek: ContentEncryptionKey, recipientPub: PublicKey): ByteArray {
        val cekBytes = cek.bytesOrThrow()
        val ephPub = ByteArray(X25519_KEY_SIZE)
        for (i in 0 until X25519_KEY_SIZE) ephPub[i] = (recipientPub.bytes[i].toInt() xor 0x5A).toByte()
        val enc = ByteArray(CEK_SIZE)
        for (i in 0 until CEK_SIZE) enc[i] = (cekBytes[i].toInt() xor recipientPub.bytes[i].toInt()).toByte()
        val mac = ByteArray(POLY1305_MAC_SIZE)
        for (i in 0 until X25519_KEY_SIZE) {
            mac[i % POLY1305_MAC_SIZE] = (mac[i % POLY1305_MAC_SIZE].toInt() xor ephPub[i].toInt() xor enc[i].toInt() xor recipientPub.bytes[i].toInt()).toByte()
        }
        val out = ByteArray(SEALED_CEK_SIZE)
        ephPub.copyInto(out, 0)
        enc.copyInto(out, 32)
        mac.copyInto(out, 64)
        return out
    }

    override fun unsealCEK(
        sealedCEK: ByteArray,
        ownPair: DeviceKeyPair,
    ): Outcome<ContentEncryptionKey, CryptoError> {
        if (sealedCEK.size != SEALED_CEK_SIZE) return Outcome.Failure(CryptoError.MalformedEnvelope())
        val pub = ownPair.publicKey.bytes
        val ephPub = sealedCEK.copyOfRange(0, 32)
        val enc = sealedCEK.copyOfRange(32, 64)
        val mac = sealedCEK.copyOfRange(64, 80)
        // verify MAC
        val expected = ByteArray(POLY1305_MAC_SIZE)
        for (i in 0 until X25519_KEY_SIZE) {
            expected[i % POLY1305_MAC_SIZE] = (expected[i % POLY1305_MAC_SIZE].toInt() xor ephPub[i].toInt() xor enc[i].toInt() xor pub[i].toInt()).toByte()
        }
        if (!mac.contentEquals(expected)) return Outcome.Failure(CryptoError.MacFailed())
        val cekBytes = ByteArray(CEK_SIZE)
        for (i in 0 until CEK_SIZE) cekBytes[i] = (enc[i].toInt() xor pub[i].toInt()).toByte()
        return Outcome.Success(ContentEncryptionKey(cekBytes))
    }
}
