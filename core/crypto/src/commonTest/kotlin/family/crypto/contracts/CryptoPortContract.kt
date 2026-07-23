package family.crypto.contracts

import family.crypto.fake.FakeCryptoPort
import family.crypto.fake.FakeGroupPort
import family.crypto.ports.CryptoPort
import family.crypto.ports.GroupId
import family.crypto.ports.GroupPort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Reusable contract for a [CryptoPort] paired with the [GroupPort] that owns its epoch state
 * (TASK-123, FR-004). Extended by the fake now; by TASK-124's real adapter later (SC-005).
 *
 * Invariants: encrypt→decrypt roundtrips; two encryptions of the same plaintext differ (nonce
 * ratchet); decrypt under the wrong group fails (cross-group isolation); a ciphertext from a prior
 * epoch cannot be decrypted after the group advances (forward secrecy).
 */
abstract class CryptoPortContract {

    /** The two ports MUST share epoch state (the real adapter shares the openmls central object). */
    class Ports(val crypto: CryptoPort, val group: GroupPort)

    abstract fun createPorts(): Ports

    @Test
    fun encryptThenDecrypt_roundtrips() = runTest {
        val (crypto, group) = createPorts().let { it.crypto to it.group }
        val g = GroupId("g1")
        group.createGroup(g)
        val plaintext = "the quick brown fox".encodeToByteArray()
        val ct = crypto.encryptMessage(g, plaintext)
        assertContentEquals(plaintext, crypto.decryptMessage(g, ct))
    }

    @Test
    fun encryptingSamePlaintextTwice_yieldsDifferentCiphertexts() = runTest {
        val ports = createPorts()
        val g = GroupId("g1")
        ports.group.createGroup(g)
        val plaintext = "same".encodeToByteArray()
        val a = ports.crypto.encryptMessage(g, plaintext)
        val b = ports.crypto.encryptMessage(g, plaintext)
        assertFalse(a.bytes.contentEquals(b.bytes), "nonce ratchet must make ciphertexts differ")
    }

    @Test
    fun decryptUnderWrongGroup_fails() = runTest {
        val ports = createPorts()
        val g1 = GroupId("g1")
        val g2 = GroupId("g2")
        ports.group.createGroup(g1)
        ports.group.createGroup(g2)
        val ct = ports.crypto.encryptMessage(g1, "secret".encodeToByteArray())
        assertFailsWith<Exception> { ports.crypto.decryptMessage(g2, ct) }
    }

    @Test
    fun ciphertextFromPriorEpoch_undecryptableAfterMerge() = runTest {
        val ports = createPorts()
        val g = GroupId("g1")
        ports.group.createGroup(g)
        val ct = ports.crypto.encryptMessage(g, "old epoch".encodeToByteArray())
        // Advance the epoch (forward secrecy): the prior epoch key is destroyed.
        ports.group.selfUpdate(g)
        ports.group.mergePendingCommit(g)
        assertFailsWith<Exception> { ports.crypto.decryptMessage(g, ct) }
    }
}

/** Binds [CryptoPortContract] to [FakeCryptoPort] + [FakeGroupPort] sharing epoch state. */
class FakeCryptoPortContractTest : CryptoPortContract() {
    override fun createPorts(): Ports {
        val group = FakeGroupPort()
        return Ports(crypto = FakeCryptoPort(group), group = group)
    }
}
