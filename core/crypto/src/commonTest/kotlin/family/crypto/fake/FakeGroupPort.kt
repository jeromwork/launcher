package family.crypto.fake

import family.crypto.ports.Commit
import family.crypto.ports.CommitBundle
import family.crypto.ports.GroupId
import family.crypto.ports.GroupPort
import family.crypto.ports.IdentityKey
import family.crypto.ports.KeyPackage
import family.crypto.ports.ProcessedMessage

/**
 * In-memory [GroupPort] fake (TASK-123, FR-003). Models the openmls two-phase commit: a mutating
 * call STAGES a [CommitBundle] and the local epoch only advances on [mergePendingCommit]. NOT real
 * crypto — it exists so consumers (messenger TASK-42, pairing TASK-67) can be built and tested
 * without Rust/openmls (rule 6, mock-first).
 *
 * Deterministic errors (spec §Edge Cases): unknown [GroupId] → [IllegalStateException];
 * double [createGroup] → [IllegalStateException].
 *
 * The per-group epoch key is exposed [internal]ly to [FakeCryptoPort] so the two fakes share epoch
 * state and jointly exhibit the forward-secrecy invariant (T008/T011).
 */
class FakeGroupPort : GroupPort {

    private class GroupData(
        var epoch: Long,
        var epochKey: ByteArray,
        var pending: Boolean,
    )

    private val groups = mutableMapOf<String, GroupData>()

    override suspend fun createGroup(groupId: GroupId) {
        check(groupId.value !in groups) { "Group already exists: ${groupId.value}" }
        groups[groupId.value] = GroupData(
            epoch = 0L,
            epochKey = FakeMlsCodec.deriveEpochKey(groupId.value),
            pending = false,
        )
    }

    override suspend fun addMembers(groupId: GroupId, keyPackages: List<KeyPackage>): CommitBundle {
        val g = groupOrThrow(groupId)
        g.pending = true
        // Adding members produces a Welcome for the new members (non-null); other commits do not.
        return CommitBundle(
            commit = commitFor(groupId, g, "add:${keyPackages.size}"),
            welcome = tagged(FakeMlsCodec.TAG_COMMIT, "welcome:${groupId.value}:${g.epoch}"),
        )
    }

    override suspend fun removeMembers(groupId: GroupId, members: List<IdentityKey>): CommitBundle {
        val g = groupOrThrow(groupId)
        g.pending = true
        return CommitBundle(commit = commitFor(groupId, g, "remove:${members.size}"), welcome = null)
    }

    override suspend fun selfUpdate(groupId: GroupId): CommitBundle {
        val g = groupOrThrow(groupId)
        g.pending = true
        return CommitBundle(commit = commitFor(groupId, g, "update"), welcome = null)
    }

    override suspend fun commitToPendingProposals(groupId: GroupId): CommitBundle? {
        groupOrThrow(groupId)
        // No proposal buffer in the fake → nothing to commit (deterministic null, FR-009 shape).
        return null
    }

    override suspend fun mergePendingCommit(groupId: GroupId) {
        val g = groupOrThrow(groupId)
        check(g.pending) { "No pending commit to merge for group: ${groupId.value}" }
        g.epoch += 1
        g.epochKey = FakeMlsCodec.ratchetForward(g.epochKey) // forward secrecy: prior key destroyed
        g.pending = false
    }

    override suspend fun processMessage(groupId: GroupId, message: ByteArray): ProcessedMessage {
        groupOrThrow(groupId)
        check(message.isNotEmpty()) { "Empty MLS message" }
        val body = message.copyOfRange(1, message.size)
        return when (message[0]) {
            FakeMlsCodec.TAG_COMMIT -> ProcessedMessage.StagedCommit(Commit(message))
            FakeMlsCodec.TAG_APPLICATION -> ProcessedMessage.ApplicationMessage(body)
            FakeMlsCodec.TAG_PROPOSAL -> ProcessedMessage.Proposal(body)
            else -> error("Unrecognized MLS message tag: ${message[0]}")
        }
    }

    // --- shared with FakeCryptoPort ------------------------------------------------------------

    internal fun currentEpochKey(groupId: GroupId): ByteArray = groupOrThrow(groupId).epochKey

    internal fun currentEpoch(groupId: GroupId): Long = groupOrThrow(groupId).epoch

    // --- internals -----------------------------------------------------------------------------

    private fun groupOrThrow(groupId: GroupId): GroupData =
        groups[groupId.value] ?: error("Unknown group: ${groupId.value}")

    private fun commitFor(groupId: GroupId, g: GroupData, kind: String): Commit =
        Commit(tagged(FakeMlsCodec.TAG_COMMIT, "${groupId.value}:${g.epoch}:$kind"))

    private fun tagged(tag: Byte, payload: String): ByteArray =
        byteArrayOf(tag) + payload.encodeToByteArray()
}
