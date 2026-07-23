package family.crypto.fake

/**
 * Shared deterministic helpers for the in-memory MLS-shaped fakes (TASK-123). NOT cryptography —
 * just enough one-way mixing and message tagging for the fakes to exhibit the invariants the
 * contract tests assert (distinct ciphertexts, forward-secrecy on merge, message-variant routing).
 */
internal object FakeMlsCodec {

    // Leading tag byte on a "wire" message, so FakeGroupPort.processMessage can route it.
    const val TAG_APPLICATION: Byte = 'A'.code.toByte()
    const val TAG_COMMIT: Byte = 'C'.code.toByte()
    const val TAG_PROPOSAL: Byte = 'P'.code.toByte()

    const val KEY_SIZE = 32

    /** Derive a stable 32-byte per-group base secret from the opaque group handle. */
    fun deriveEpochKey(seed: String): ByteArray = mix(seed.encodeToByteArray(), KEY_SIZE)

    /**
     * One-way forward ratchet: the new key is derived from the old one, and the fake keeps ONLY
     * the current key — so once merged, the prior epoch's key is unreproducible (fake models
     * forward secrecy; spec Assumption).
     */
    fun ratchetForward(key: ByteArray): ByteArray = mix(key + FORWARD_SALT, KEY_SIZE)

    /** Keystream for a (key, nonce) pair — repeating fold, XOR'd against the plaintext. */
    fun keystream(key: ByteArray, nonce: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        for (i in 0 until length) {
            out[i] = (key[i % key.size].toInt() xor nonce[i % nonce.size].toInt()).toByte()
        }
        return out
    }

    /** Deterministic MAC over ciphertext under a key (integrity-ish, not real). */
    fun mac(ciphertext: ByteArray, key: ByteArray, size: Int): ByteArray {
        val m = ByteArray(size)
        for (i in ciphertext.indices) {
            m[i % size] = (m[i % size].toInt() xor ciphertext[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return m
    }

    // FNV-1a-style avalanche producing `size` bytes; deterministic, one-way enough for a fake.
    private fun mix(input: ByteArray, size: Int): ByteArray {
        var h = -0x7ee3623b // 0x811c9dc5
        val out = ByteArray(size)
        for (b in input) {
            h = h xor (b.toInt() and 0xff)
            h *= 0x01000193
        }
        for (i in 0 until size) {
            h = h xor i
            h *= 0x01000193
            out[i] = (h ushr ((i % 4) * 8)).toByte()
        }
        return out
    }

    private val FORWARD_SALT = byteArrayOf(0x52, 0x54, 0x43, 0x48) // "RTCH"
}
