@file:OptIn(ExperimentalUnsignedTypes::class)

package family.crypto.libsodium

import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import family.crypto.api.RandomSource
import family.crypto.exception.CryptoException

/**
 * Real [RandomSource] backed by libsodium `randombytes_buf` (FR-013).
 *
 * Underlying source is the platform CSPRNG (libsodium picks `/dev/urandom`, BCryptGenRandom,
 * etc.). See libsodium docs for full list.
 */
class LibsodiumRandomSource : RandomSource {

    override suspend fun nextBytes(size: Int): ByteArray {
        require(size >= 0) { "size must be >= 0" }
        if (size == 0) return ByteArray(0)
        LibsodiumInit.ensure()
        return try {
            LibsodiumRandom.buf(size).asByteArray()
        } catch (t: Throwable) {
            throw CryptoException.RandomSourceUnavailable(t)
        }
    }
}
