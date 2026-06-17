package family.crypto.property

import family.crypto.fake.FakeAsymmetricCrypto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class EcdhSymmetryPropertyTest {

    @Test
    fun dhSymmetric_1000Iterations() = runTest {
        val crypto = FakeAsymmetricCrypto(seed = 7)
        repeat(1000) {
            val kpA = crypto.generateX25519KeyPair()
            val kpB = crypto.generateX25519KeyPair()
            val s1 = crypto.deriveSharedSecret(kpA.privateKey, kpB.publicKey)
            val s2 = crypto.deriveSharedSecret(kpB.privateKey, kpA.publicKey)
            assertContentEquals(s1.bytes, s2.bytes)
        }
    }
}
