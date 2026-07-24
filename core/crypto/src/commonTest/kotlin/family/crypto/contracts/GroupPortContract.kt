package family.crypto.contracts

import family.crypto.fake.FakeGroupPort
import family.crypto.ports.GroupId
import family.crypto.ports.GroupPort
import family.crypto.ports.KeyPackage
import family.crypto.ports.ProcessedMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reusable contract for any [GroupPort] implementation (TASK-123, FR-004). The fake binds it now
 * ([FakeGroupPortContractTest]); TASK-124's real openmls adapter extends the SAME class from
 * `androidUnitTest` (which sees `commonTest` via the KMP source-set hierarchy) so no test code
 * moves when the real adapter lands (SC-005).
 *
 * Invariants (spec §Edge Cases): create; double-create → error; add → CommitBundle (+Welcome);
 * remove/selfUpdate → CommitBundle (no Welcome); processMessage → correct variant; unknown group
 * → error; two-phase merge state machine.
 */
abstract class GroupPortContract {

    abstract fun createGroupPort(): GroupPort

    /** Impl-specific sample bytes decoded as an application message; `null` skips that check. */
    open fun applicationMessageBytes(): ByteArray? = null

    /** Impl-specific sample bytes decoded as a proposal; `null` skips that check. */
    open fun proposalBytes(): ByteArray? = null

    /**
     * A KeyPackage this implementation accepts in [GroupPort.addMembers]. The fake takes any bytes;
     * a real MLS engine needs a genuine RFC 9420 KeyPackage, so the real adapter generates one
     * (TASK-124).
     */
    protected open suspend fun keyPackageFor(tag: String): KeyPackage =
        KeyPackage(tag.encodeToByteArray())

    /**
     * Whether the implementation can feed its OWN commit back through [GroupPort.processMessage].
     *
     * The fake can. Real MLS cannot: RFC 9420 forbids a member from unprotecting its own message
     * ("Cannot decrypt own messages") — an own commit is applied via [GroupPort.mergePendingCommit],
     * and only a PEER's commit is ever processed. When `false`, the contract asserts the
     * deterministic error instead (TASK-124).
     */
    protected open fun canProcessOwnCommit(): Boolean = true

    @Test
    fun createGroup_succeeds() = runTest {
        createGroupPort().createGroup(GroupId("g1"))
    }

    @Test
    fun createGroup_twice_isDeterministicError() = runTest {
        val port = createGroupPort()
        val g = GroupId("g1")
        port.createGroup(g)
        assertFailsWith<Exception> { port.createGroup(g) }
    }

    @Test
    fun addMembers_returnsCommitBundleWithWelcome() = runTest {
        val port = createGroupPort()
        val g = GroupId("g1")
        port.createGroup(g)
        val bundle = port.addMembers(g, listOf(keyPackageFor("alice"), keyPackageFor("bob")))
        assertTrue(bundle.commit.bytes.isNotEmpty(), "commit must be non-empty")
        assertNotNull(bundle.welcome, "adding members must produce a Welcome")
    }

    @Test
    fun selfUpdate_returnsCommitBundleWithoutWelcome() = runTest {
        val port = createGroupPort()
        val g = GroupId("g1")
        port.createGroup(g)
        val bundle = port.selfUpdate(g)
        assertTrue(bundle.commit.bytes.isNotEmpty())
        assertNull(bundle.welcome, "selfUpdate must not produce a Welcome")
    }

    @Test
    fun processMessage_commitBytes_isStagedCommit() = runTest {
        val port = createGroupPort()
        val g = GroupId("g1")
        port.createGroup(g)
        val bundle = port.addMembers(g, listOf(keyPackageFor("carol")))
        if (canProcessOwnCommit()) {
            assertIs<ProcessedMessage.StagedCommit>(port.processMessage(g, bundle.commit.bytes))
        } else {
            assertFailsWith<Exception> { port.processMessage(g, bundle.commit.bytes) }
        }
    }

    @Test
    fun processMessage_applicationBytes_isApplicationMessage() = runTest {
        val bytes = applicationMessageBytes() ?: return@runTest
        val port = createGroupPort()
        val g = GroupId("g1")
        port.createGroup(g)
        assertIs<ProcessedMessage.ApplicationMessage>(port.processMessage(g, bytes))
    }

    @Test
    fun processMessage_proposalBytes_isProposal() = runTest {
        val bytes = proposalBytes() ?: return@runTest
        val port = createGroupPort()
        val g = GroupId("g1")
        port.createGroup(g)
        assertIs<ProcessedMessage.Proposal>(port.processMessage(g, bytes))
    }

    @Test
    fun unknownGroup_isDeterministicError() = runTest {
        assertFailsWith<Exception> {
            createGroupPort().addMembers(GroupId("nope"), listOf(keyPackageFor("x")))
        }
    }

    @Test
    fun mergePendingCommit_twoPhaseStateMachine() = runTest {
        val port = createGroupPort()
        val g = GroupId("g1")
        port.createGroup(g)
        // Nothing staged yet → merge is a deterministic error.
        assertFailsWith<Exception> { port.mergePendingCommit(g) }
        // Stage then merge → succeeds.
        port.selfUpdate(g)
        port.mergePendingCommit(g)
    }
}

/** Binds [GroupPortContract] to [FakeGroupPort] (TASK-123). */
class FakeGroupPortContractTest : GroupPortContract() {
    override fun createGroupPort(): GroupPort = FakeGroupPort()
    override fun applicationMessageBytes(): ByteArray = byteArrayOf('A'.code.toByte()) + "hello".encodeToByteArray()
    override fun proposalBytes(): ByteArray = byteArrayOf('P'.code.toByte()) + "prop".encodeToByteArray()
}
