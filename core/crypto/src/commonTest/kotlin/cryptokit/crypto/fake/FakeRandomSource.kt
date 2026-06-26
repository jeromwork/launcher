package cryptokit.crypto.fake

import cryptokit.crypto.api.RandomSource
import kotlin.random.Random

/**
 * TEST-ONLY [RandomSource]. Seeded for reproducibility — must NEVER be used in production.
 */
class FakeRandomSource(seed: Long = 0xC0FFEEL) : RandomSource {
    private val rng = Random(seed)

    override suspend fun nextBytes(size: Int): ByteArray = rng.nextBytes(size)
}
