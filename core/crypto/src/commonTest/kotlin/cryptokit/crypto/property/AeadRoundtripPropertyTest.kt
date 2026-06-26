package cryptokit.crypto.property

import cryptokit.crypto.exception.CryptoException
import cryptokit.crypto.fake.FakeAeadCipher
import cryptokit.crypto.fake.FakeRandomSource
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class AeadRoundtripPropertyTest {

    @Test
    fun encryptThenDecryptRoundtrips_1000Iterations() = runTest {
        val rng = Random(seed = 12345)
        val cipher = FakeAeadCipher(random = FakeRandomSource(seed = 0xC0FFEEL))

        repeat(1000) {
            val plaintext = rng.nextBytes(rng.nextInt(0, 256))
            val key = rng.nextBytes(32)
            val aad = rng.nextBytes(rng.nextInt(0, 64))
            val ct = cipher.encrypt(plaintext, key, aad)
            val pt = cipher.decrypt(ct, key, aad)
            assertContentEquals(plaintext, pt)
        }
    }

    @Test
    fun tamperingFlipsOneBit_DecryptionThrows() = runTest {
        val rng = Random(seed = 67890)
        val cipher = FakeAeadCipher(random = FakeRandomSource(seed = 1L))

        repeat(200) {
            val plaintext = rng.nextBytes(rng.nextInt(1, 128))
            val key = rng.nextBytes(32)
            val aad = rng.nextBytes(rng.nextInt(0, 32))
            val ct = cipher.encrypt(plaintext, key, aad)
            // Flip one bit in a random position inside the MAC region (last 16 bytes).
            val bytes = ct.bytes.copyOf()
            val pos = bytes.size - 1 - rng.nextInt(0, 16)
            bytes[pos] = (bytes[pos].toInt() xor 0x01).toByte()
            val tampered = cryptokit.crypto.api.values.Ciphertext(bytes)
            assertFailsWith<CryptoException.DecryptionFailed> {
                cipher.decrypt(tampered, key, aad)
            }
        }
    }
}
