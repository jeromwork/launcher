package cryptokit.crypto.property

import cryptokit.crypto.api.RandomSource
import cryptokit.crypto.exception.CryptoException
import cryptokit.crypto.fake.FakeAeadCipher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NonceReuseRejectionPropertyTest {

    @Test
    fun encryptingTwiceWithSameKeyAndNonce_throws() = runTest {
        // Force the same nonce on every call via a fixed RandomSource.
        val constantNonce = ByteArray(24) { it.toByte() }
        val fixed = object : RandomSource {
            override suspend fun nextBytes(size: Int): ByteArray =
                if (size == 24) constantNonce.copyOf() else ByteArray(size)
        }
        val cipher = FakeAeadCipher(random = fixed)
        val key = ByteArray(32) { 0x42 }

        cipher.encrypt(byteArrayOf(1, 2, 3), key)
        assertFailsWith<CryptoException.NonceReuseDetected> {
            cipher.encrypt(byteArrayOf(4, 5, 6), key)
        }
    }
}
