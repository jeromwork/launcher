package cryptokit.crypto.property

import cryptokit.crypto.api.values.Signature
import cryptokit.crypto.fake.FakeAsymmetricCrypto
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignVerifyTamperPropertyTest {

    @Test
    fun signVerifyRoundtrip_1000Iterations() = runTest {
        val crypto = FakeAsymmetricCrypto(seed = 11)
        val rng = Random(seed = 42)
        repeat(1000) {
            val kp = crypto.generateEd25519KeyPair()
            val msg = rng.nextBytes(rng.nextInt(1, 256))
            val sig = crypto.sign(msg, kp.privateKey)
            assertTrue(crypto.verify(sig, msg, kp.publicKey), "valid signature must verify")
        }
    }

    @Test
    fun tamperedSignature_FailsVerify_200Iterations() = runTest {
        val crypto = FakeAsymmetricCrypto(seed = 99)
        val rng = Random(seed = 4242)
        repeat(200) {
            val kp = crypto.generateEd25519KeyPair()
            val msg = rng.nextBytes(rng.nextInt(1, 64))
            val sig = crypto.sign(msg, kp.privateKey)
            val tampered = sig.bytes.copyOf()
            val pos = rng.nextInt(tampered.size)
            tampered[pos] = (tampered[pos].toInt() xor 0x01).toByte()
            assertFalse(crypto.verify(Signature(tampered), msg, kp.publicKey))
        }
    }

    @Test
    fun tamperedMessage_FailsVerify_200Iterations() = runTest {
        val crypto = FakeAsymmetricCrypto(seed = 31)
        val rng = Random(seed = 555)
        repeat(200) {
            val kp = crypto.generateEd25519KeyPair()
            val msg = rng.nextBytes(rng.nextInt(1, 64))
            val sig = crypto.sign(msg, kp.privateKey)
            val tamperedMsg = msg.copyOf()
            val pos = rng.nextInt(tamperedMsg.size)
            tamperedMsg[pos] = (tamperedMsg[pos].toInt() xor 0x01).toByte()
            assertFalse(crypto.verify(sig, tamperedMsg, kp.publicKey))
        }
    }
}
