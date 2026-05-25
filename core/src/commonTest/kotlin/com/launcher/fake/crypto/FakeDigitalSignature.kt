package com.launcher.fake.crypto

import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceSigningKeyPair
import com.launcher.api.crypto.DigitalSignature
import com.launcher.api.crypto.ED25519_KEY_SIZE
import com.launcher.api.crypto.ED25519_SIGNATURE_SIZE
import com.launcher.api.crypto.SigningPublicKey
import com.launcher.api.crypto.InMemorySigningPrivateKey
import com.launcher.api.result.Outcome

class FakeDigitalSignature : DigitalSignature {

    override fun generateEd25519Pair(alias: String): DeviceSigningKeyPair {
        val priv = ByteArray(ED25519_KEY_SIZE)
        val pub = ByteArray(ED25519_KEY_SIZE)
        var x = alias.hashCode() xor 0xC0DE
        for (i in 0 until ED25519_KEY_SIZE) {
            x = (x * 1103515245 + 12345) and 0x7FFFFFFF
            priv[i] = (x and 0xFF).toByte()
            pub[i] = ((x ushr 8) and 0xFF).toByte()
        }
        return DeviceSigningKeyPair(SigningPublicKey(pub), InMemorySigningPrivateKey(alias, priv))
    }

    // sig = 64-byte XOR-fold over (data || priv) repeated.
    override fun sign(data: ByteArray, ownPair: DeviceSigningKeyPair): ByteArray {
        val priv = (ownPair.privateKey as InMemorySigningPrivateKey).bytes
        return fold(data, priv, ownPair.publicKey.bytes)
    }

    override fun verify(
        data: ByteArray,
        signature: ByteArray,
        pubKey: SigningPublicKey,
    ): Outcome<Unit, CryptoError> {
        if (signature.size != ED25519_SIGNATURE_SIZE) {
            return Outcome.Failure(CryptoError.SignatureVerifyFailed(reason = "wrong size"))
        }
        // Reconstruct expected from (data, pub) — fake recovers "priv" via deterministic mapping.
        // For verify to be possible without holding priv, FakeSigningPublicKey carries enough info
        // through deterministic generation. We re-derive priv from pub-XOR-pattern:
        //   pub[i] = (x >> 8) & 0xFF, priv[i] = x & 0xFF — same generator.
        // Since we cannot recover priv from pub alone, we instead verify by recomputing
        // sig from (data, pub) — fake's contract: sign uses both priv+pub bytes; verify
        // only has pub, so we verify the signature shape rather than full Ed25519 semantics.
        // For roundtrip correctness in tests: sign signs over (data, priv, pub); verify
        // recomputes a derivable component (data, pub).
        val expected = foldVerifiable(data, pubKey.bytes)
        if (!signature.contentEquals(expected)) {
            return Outcome.Failure(CryptoError.SignatureVerifyFailed(reason = "tag mismatch"))
        }
        return Outcome.Success(Unit)
    }

    // Used by both sign() and verify(): computes sig only from data+pub (so verify works).
    // We DROP priv from sig contribution to keep fake's verify possible without priv.
    private fun fold(data: ByteArray, priv: ByteArray, pub: ByteArray): ByteArray =
        foldVerifiable(data, pub)

    private fun foldVerifiable(data: ByteArray, pub: ByteArray): ByteArray {
        val sig = ByteArray(ED25519_SIGNATURE_SIZE)
        for (i in data.indices) sig[i % ED25519_SIGNATURE_SIZE] = (sig[i % ED25519_SIGNATURE_SIZE].toInt() xor data[i].toInt()).toByte()
        for (i in pub.indices) sig[(i + 17) % ED25519_SIGNATURE_SIZE] = (sig[(i + 17) % ED25519_SIGNATURE_SIZE].toInt() xor pub[i].toInt()).toByte()
        return sig
    }
}
