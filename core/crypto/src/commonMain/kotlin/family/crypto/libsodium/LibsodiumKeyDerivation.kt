package family.crypto.libsodium

import family.crypto.api.KeyDerivation

/**
 * Real HKDF-SHA256 per RFC 5869, used as the project's default [KeyDerivation] (FR-008, FR-013).
 *
 * ionspin libsodium 0.9.5 does not expose `crypto_kdf_hkdf_sha256` in its KMP API
 * (see research.md §R1 status note 2026-06-17), so we implement RFC 5869 §2.2 (Extract)
 * + §2.3 (Expand) directly over a platform-provided [HmacSha256].
 *
 * **Properties**:
 *  • Extract: `PRK = HMAC-SHA256(salt, IKM)`; if `salt` is empty, RFC 5869 §2.2 mandates
 *    using `HashLen` zero bytes (32 bytes here).
 *  • Expand: iterates `T(i) = HMAC-SHA256(PRK, T(i-1) || info || i)` until [length] bytes.
 *  • [length] must be `≤ 255 * 32 = 8160` bytes (HKDF upper bound).
 */
class LibsodiumKeyDerivation : KeyDerivation {

    override suspend fun derive(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
        require(length in 1..MAX_LENGTH) {
            "HKDF length must be in 1..$MAX_LENGTH, got $length"
        }
        val saltOrZeros = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        val prk = HmacSha256.mac(saltOrZeros, ikm)
        val infoBytes = info

        val n = (length + HASH_LEN - 1) / HASH_LEN
        val out = ByteArray(length)
        var prev = ByteArray(0)
        var written = 0
        for (i in 1..n) {
            val input = ByteArray(prev.size + infoBytes.size + 1)
            prev.copyInto(input, 0)
            infoBytes.copyInto(input, prev.size)
            input[input.size - 1] = i.toByte()
            val t = HmacSha256.mac(prk, input)
            val toCopy = minOf(HASH_LEN, length - written)
            t.copyInto(out, written, 0, toCopy)
            written += toCopy
            prev = t
        }
        return out
    }

    companion object {
        const val HASH_LEN = 32
        const val MAX_LENGTH = 255 * HASH_LEN
    }
}
