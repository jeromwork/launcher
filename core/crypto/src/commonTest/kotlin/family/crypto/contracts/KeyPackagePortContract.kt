package family.crypto.contracts

import family.crypto.fake.FakeKeyPackagePort
import family.crypto.ports.ClaimResult
import family.crypto.ports.IdentityKey
import family.crypto.ports.KeyPackage
import family.crypto.ports.KeyPackagePort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Reusable contract for a [KeyPackagePort] (TASK-123, FR-004, FR-011). Extended by the fake now;
 * by TASK-124's real adapter later (SC-005).
 *
 * Invariants (spec §Edge Cases): publish→claim pops exactly one; a one-time package is consumed
 * once; an exhausted pool with no last resort returns [ClaimResult.Empty] (never throws); a
 * published last-resort key is returned (reusable) when the one-time pool is empty.
 */
abstract class KeyPackagePortContract {

    abstract fun createKeyPackagePort(): KeyPackagePort

    private fun kp(tag: String) = KeyPackage(tag.encodeToByteArray())
    private val target = IdentityKey("target".encodeToByteArray())

    @Test
    fun publishThenClaim_popsOne() = runTest {
        val port = createKeyPackagePort()
        port.publish(listOf(kp("k1"), kp("k2")), isLastResort = false)
        assertEquals(2, port.localCount())

        val claimed = port.claim(target)
        assertIs<ClaimResult.Claimed>(claimed)
        assertFalse(claimed.isLastResort)
        assertEquals(1, port.localCount(), "claim must consume exactly one package")
    }

    @Test
    fun claimOnEmptyPool_returnsEmpty_neverThrows() = runTest {
        val port = createKeyPackagePort()
        assertIs<ClaimResult.Empty>(port.claim(target))
    }

    @Test
    fun oneTimePackage_consumedExactlyOnce() = runTest {
        val port = createKeyPackagePort()
        port.publish(listOf(kp("only")), isLastResort = false)
        assertIs<ClaimResult.Claimed>(port.claim(target))
        assertIs<ClaimResult.Empty>(port.claim(target))
    }

    @Test
    fun lastResortKey_returnedWhenPoolEmpty_andReusable() = runTest {
        val port = createKeyPackagePort()
        port.publish(listOf(kp("last-resort")), isLastResort = true)

        val first = port.claim(target)
        assertIs<ClaimResult.Claimed>(first)
        assertTrue(first.isLastResort, "empty one-time pool must fall back to the last-resort key")

        // Reusable — a second claim still yields the last-resort key, not Empty.
        val second = port.claim(target)
        assertIs<ClaimResult.Claimed>(second)
        assertTrue(second.isLastResort)
    }

    @Test
    fun oneTimePool_preferredOverLastResort() = runTest {
        val port = createKeyPackagePort()
        port.publish(listOf(kp("last-resort")), isLastResort = true)
        port.publish(listOf(kp("one-time")), isLastResort = false)

        val claimed = port.claim(target)
        assertIs<ClaimResult.Claimed>(claimed)
        assertFalse(claimed.isLastResort, "a one-time package is preferred while the pool is non-empty")
    }
}

/** Binds [KeyPackagePortContract] to [FakeKeyPackagePort] (TASK-123). */
class FakeKeyPackagePortContractTest : KeyPackagePortContract() {
    override fun createKeyPackagePort(): KeyPackagePort = FakeKeyPackagePort()
}
