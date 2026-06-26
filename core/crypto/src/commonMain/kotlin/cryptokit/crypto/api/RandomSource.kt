package cryptokit.crypto.api

/** Cryptographically-secure random byte source. Per FR-009. */
interface RandomSource {
    /**
     * @param size number of random bytes to generate.
     * @throws cryptokit.crypto.exception.CryptoException.RandomSourceUnavailable
     *   if entropy source is unavailable.
     */
    suspend fun nextBytes(size: Int): ByteArray
}
