package cryptokit.crypto.fake

import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.values.KeyPair
import cryptokit.crypto.api.values.SealedBlob
import cryptokit.crypto.api.values.SharedSecret
import cryptokit.crypto.api.values.Signature
import cryptokit.crypto.exception.CryptoException

/**
 * TEST-ONLY [AsymmetricCrypto]. NOT real cryptography.
 *
 * Properties intentionally preserved so consumer tests can rely on roundtrip semantics:
 *  • Keypair generation: deterministic via internal counter (seeded from constructor).
 *  • ECDH symmetric: `DH(a, B) == DH(b, A)` — implemented as `sort(a, b) XOR-fold`.
 *  • Sign/verify: deterministic XOR-based "MAC" over message+priv; verify recomputes.
 *  • Sealed-box: `payload XOR recipientPub` prefixed by an ephemeral pub.
 */
class FakeAsymmetricCrypto(seed: Int = 0) : AsymmetricCrypto {

    private var x25519Counter: Int = seed
    private var ed25519Counter: Int = seed + 100_000

    override suspend fun generateX25519KeyPair(): KeyPair {
        val n = x25519Counter++
        return makePair("X25519", n)
    }

    override suspend fun generateEd25519KeyPair(): KeyPair {
        val n = ed25519Counter++
        return makePair("Ed25519", n)
    }

    override suspend fun deriveSharedSecret(myPrivate: ByteArray, theirPublic: ByteArray): SharedSecret {
        require(myPrivate.size == KEY_SIZE) { "myPrivate must be $KEY_SIZE bytes" }
        require(theirPublic.size == KEY_SIZE) { "theirPublic must be $KEY_SIZE bytes" }
        if (theirPublic.all { it == 0.toByte() }) {
            throw CryptoException.InvalidPublicKey("All-zero public key rejected")
        }
        val myPub = derivePublic(myPrivate)
        // Sort priv-derived-pub and peer-pub to enforce symmetry: DH(a,B) == DH(b,A).
        val sorted = listOf(myPub, theirPublic).sortedWith(byteArrayComparator())
        val secret = ByteArray(KEY_SIZE)
        for (i in 0 until KEY_SIZE) {
            secret[i] = (sorted[0][i].toInt() xor sorted[1][i].toInt()).toByte()
        }
        return SharedSecret(secret)
    }

    override suspend fun sign(message: ByteArray, privateKey: ByteArray): Signature {
        require(privateKey.size == KEY_SIZE) { "privateKey must be $KEY_SIZE bytes" }
        val sig = ByteArray(SIG_SIZE)
        for (i in 0 until SIG_SIZE) {
            sig[i] = (privateKey[i % KEY_SIZE].toInt() xor
                (if (message.isNotEmpty()) message[i % message.size].toInt() else 0) xor
                (i and 0xff)).toByte()
        }
        return Signature(sig)
    }

    override suspend fun verify(signature: Signature, message: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.bytes.size != SIG_SIZE) return false
        if (publicKey.size != KEY_SIZE) return false
        val privateKey = privFromPub(publicKey) ?: return false
        val expected = sign(message, privateKey)
        return expected.bytes.contentEquals(signature.bytes)
    }

    override suspend fun sealForRecipient(payload: ByteArray, recipientPublicKey: ByteArray): SealedBlob {
        require(recipientPublicKey.size == KEY_SIZE) { "recipientPublicKey must be $KEY_SIZE bytes" }
        val ephemeralPub = ByteArray(KEY_SIZE) { ((it + payload.size) and 0xff).toByte() }
        val ct = ByteArray(payload.size)
        for (i in payload.indices) {
            ct[i] = (payload[i].toInt() xor recipientPublicKey[i % KEY_SIZE].toInt()).toByte()
        }
        val mac = ByteArray(MAC_SIZE)
        for (i in ct.indices) {
            mac[i % MAC_SIZE] = (mac[i % MAC_SIZE].toInt() xor ct[i].toInt() xor
                recipientPublicKey[i % KEY_SIZE].toInt()).toByte()
        }
        val out = ByteArray(KEY_SIZE + ct.size + MAC_SIZE)
        ephemeralPub.copyInto(out, 0)
        ct.copyInto(out, KEY_SIZE)
        mac.copyInto(out, KEY_SIZE + ct.size)
        return SealedBlob(out)
    }

    override suspend fun openSealed(blob: SealedBlob, recipientPrivateKey: ByteArray): ByteArray {
        require(recipientPrivateKey.size == KEY_SIZE) { "recipientPrivateKey must be $KEY_SIZE bytes" }
        val bytes = blob.bytes
        if (bytes.size < KEY_SIZE + MAC_SIZE) {
            throw CryptoException.DecryptionFailed("Sealed blob too short")
        }
        val ct = bytes.copyOfRange(KEY_SIZE, bytes.size - MAC_SIZE)
        val mac = bytes.copyOfRange(bytes.size - MAC_SIZE, bytes.size)
        val recipientPub = derivePublic(recipientPrivateKey)
        val expectedMac = ByteArray(MAC_SIZE)
        for (i in ct.indices) {
            expectedMac[i % MAC_SIZE] = (expectedMac[i % MAC_SIZE].toInt() xor ct[i].toInt() xor
                recipientPub[i % KEY_SIZE].toInt()).toByte()
        }
        if (!mac.contentEquals(expectedMac)) {
            throw CryptoException.DecryptionFailed("Sealed blob MAC mismatch")
        }
        val pt = ByteArray(ct.size)
        for (i in ct.indices) {
            pt[i] = (ct[i].toInt() xor recipientPub[i % KEY_SIZE].toInt()).toByte()
        }
        return pt
    }

    private fun makePair(algorithm: String, n: Int): KeyPair {
        val priv = ByteArray(KEY_SIZE) { (n + it).toByte() }
        val pub = derivePublic(priv)
        return KeyPair(privateKey = priv, publicKey = pub, algorithm = algorithm)
    }

    private fun derivePublic(priv: ByteArray): ByteArray {
        // Reversible deterministic mapping: pub[i] = priv[i] XOR 0xA5.
        // Allows verify() to "recover" priv from pub for the Fake.
        val pub = ByteArray(KEY_SIZE)
        for (i in 0 until KEY_SIZE) {
            pub[i] = (priv[i].toInt() xor PUB_MASK).toByte()
        }
        return pub
    }

    private fun privFromPub(pub: ByteArray): ByteArray? {
        if (pub.size != KEY_SIZE) return null
        val priv = ByteArray(KEY_SIZE)
        for (i in 0 until KEY_SIZE) {
            priv[i] = (pub[i].toInt() xor PUB_MASK).toByte()
        }
        return priv
    }

    private fun byteArrayComparator(): Comparator<ByteArray> = Comparator { a, b ->
        for (i in 0 until minOf(a.size, b.size)) {
            val cmp = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
            if (cmp != 0) return@Comparator cmp
        }
        a.size - b.size
    }

    companion object {
        const val KEY_SIZE = 32
        const val SIG_SIZE = 64
        const val MAC_SIZE = 16
        private const val PUB_MASK = 0xA5
    }
}
