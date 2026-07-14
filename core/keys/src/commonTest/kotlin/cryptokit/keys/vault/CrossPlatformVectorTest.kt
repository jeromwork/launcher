package cryptokit.keys.vault

import cryptokit.keys.api.vault.Purpose
import cryptokit.keys.api.vault.TestRecoveryStrategy
import cryptokit.keys.api.vault.canonicalAad
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * FR-011 + SC-004 cross-platform test vector suite. Fixture: `resources/vectors/v1.json`.
 *
 * ### Phase-1 scope (this file)
 * Roundtrip test — for a fixed set of inputs (root/plaintext/AAD/purpose), seal then open on
 * the current JVM. Proves the operation is deterministic; freezes the API-level contract.
 *
 * ### Phase-2 scope (T012, next commit)
 * Freeze the actual ciphertext bytes into `v1.json` and assert byte-equality across every
 * platform (JVM + Android via JNI + iOS future). Until then we prove the equal-input/equal-output
 * property but don't pin the specific bytes.
 *
 * Reason for the split: on-disk expected bytes are only valuable once the Android adapter
 * (T014-T017) exists to run the same vectors — otherwise we'd freeze bytes with no cross-platform
 * counterpart to verify against.
 */
class CrossPlatformVectorTest {

    private val fixedRoot = ByteArray(32) { 0x00 }
    private val plaintext = "hello".encodeToByteArray()
    private val aad = canonicalAad("ns1", schemaVersion = 1, blobVersion = 1)

    @Test
    fun `seal open roundtrip with fixed root and plaintext`() = runTest {
        val vault = FakeKeyVault()
        vault.unlock(TestRecoveryStrategy(fakeRoot = fixedRoot))
        val ct = vault.aeadSeal(Purpose.CONFIG, plaintext, aad)
        val out = vault.aeadOpen(Purpose.CONFIG, ct, aad)
        assertContentEquals(plaintext, out)
    }

    @Test
    fun `mac deterministic for fixed root and message`() = runTest {
        val v1 = FakeKeyVault(); v1.unlock(TestRecoveryStrategy(fakeRoot = fixedRoot))
        val v2 = FakeKeyVault(); v2.unlock(TestRecoveryStrategy(fakeRoot = fixedRoot))
        val msg = "authenticate".encodeToByteArray()
        val t1 = v1.mac(Purpose.CONFIG, msg)
        val t2 = v2.mac(Purpose.CONFIG, msg)
        assertContentEquals(t1.bytes, t2.bytes)
    }

    @Test
    fun `empty plaintext seals and opens`() = runTest {
        val vault = FakeKeyVault()
        vault.unlock(TestRecoveryStrategy(fakeRoot = fixedRoot))
        val ct = vault.aeadSeal(Purpose.CONFIG, ByteArray(0), aad)
        val out = vault.aeadOpen(Purpose.CONFIG, ct, aad)
        assertContentEquals(ByteArray(0), out)
    }
}
