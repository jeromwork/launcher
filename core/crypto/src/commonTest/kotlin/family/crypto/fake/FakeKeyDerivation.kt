package family.crypto.fake

import family.crypto.api.KeyDerivation

/**
 * TEST-ONLY [KeyDerivation]. Deterministic HKDF-like expansion via FNV-1a hashing.
 * NOT real HKDF — sufficient for testing roundtrip / domain separation properties.
 */
class FakeKeyDerivation : KeyDerivation {

    override suspend fun derive(ikm: ByteArray, salt: ByteArray, info: String, length: Int): ByteArray {
        require(length > 0) { "length must be > 0" }
        val out = ByteArray(length)
        var hash = FNV_OFFSET
        for (b in salt) hash = mix(hash, b.toLong())
        for (b in ikm) hash = mix(hash, b.toLong())
        for (b in info.encodeToByteArray()) hash = mix(hash, b.toLong())
        for (i in 0 until length) {
            hash = mix(hash, i.toLong())
            out[i] = (hash and 0xff).toByte()
        }
        return out
    }

    private fun mix(state: Long, byte: Long): Long {
        var h = state xor (byte and 0xff)
        h *= FNV_PRIME
        // Keep within Long range; emulate 64-bit FNV-1a.
        return h
    }

    companion object {
        private const val FNV_OFFSET: Long = -3750763034362895579L // 0xcbf29ce484222325
        private const val FNV_PRIME: Long = 1099511628211L         // 0x100000001b3
    }
}
