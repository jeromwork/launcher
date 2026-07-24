package family.crypto.mls

import family.crypto.ports.Commit
import family.crypto.ports.CommitBundle
import family.crypto.ports.GroupId
import family.crypto.ports.GroupPort
import family.crypto.ports.IdentityKey
import family.crypto.ports.KeyPackage
import family.crypto.ports.ProcessedMessage
import uniffi.crypto_ffi.ProcessedKind
import uniffi.crypto_ffi.mlsAddMembers
import uniffi.crypto_ffi.mlsCommitToPendingProposals
import uniffi.crypto_ffi.mlsCreateGroup
import uniffi.crypto_ffi.mlsJoinFromWelcome
import uniffi.crypto_ffi.mlsMergePendingCommit
import uniffi.crypto_ffi.mlsMergeStagedCommit
import uniffi.crypto_ffi.mlsProcessMessage
import uniffi.crypto_ffi.mlsRemoveMembers
import uniffi.crypto_ffi.mlsSelfUpdate

/**
 * Real [GroupPort] over openmls 0.8.1 (TASK-124, T011) — replaces `FakeGroupPort` in the real
 * backend graph. The vendor never surfaces: every `uniffi.crypto_ffi` type is mapped to a domain
 * type here, and this package is the ONLY place allowed to import it (fitness-gated,
 * `PortsNoVendorImportTest`).
 *
 * Share the [store] with [OpenMlsCryptoPort] and [OpenMlsKeyPackagePort] — the three ports are one
 * device's crypto state (the contract tests assert exactly this).
 *
 * State is in-memory only (TASK-125 adds SQLCipher persistence) and the signing key is ephemeral,
 * generated per group in Rust. TODO(task-112): bind it to `KeyVault.exportDerivedKey(MLS_SIGNATURE)`.
 */
class OpenMlsGroupPort internal constructor(
    private val store: MlsSnapshotStore,
) : GroupPort {

    constructor() : this(MlsSnapshotStore())

    override suspend fun createGroup(groupId: GroupId) {
        store.mutate { state ->
            val result = mlsCreateGroup(state, groupId.toBytes())
            result.state to Unit
        }
    }

    override suspend fun addMembers(groupId: GroupId, keyPackages: List<KeyPackage>): CommitBundle =
        store.mutate { state ->
            val result = mlsAddMembers(state, groupId.toBytes(), keyPackages.map { it.bytes })
            result.state to CommitBundle(Commit(result.commit), result.welcome)
        }

    override suspend fun removeMembers(groupId: GroupId, members: List<IdentityKey>): CommitBundle =
        store.mutate { state ->
            val result = mlsRemoveMembers(state, groupId.toBytes(), members.map { it.bytes })
            result.state to CommitBundle(Commit(result.commit), result.welcome)
        }

    override suspend fun selfUpdate(groupId: GroupId): CommitBundle =
        store.mutate { state ->
            val result = mlsSelfUpdate(state, groupId.toBytes())
            result.state to CommitBundle(Commit(result.commit), result.welcome)
        }

    override suspend fun commitToPendingProposals(groupId: GroupId): CommitBundle? =
        store.mutate { state ->
            val result = mlsCommitToPendingProposals(state, groupId.toBytes())
            val bundle = result.commit?.let { CommitBundle(Commit(it), result.welcome) }
            result.state to bundle
        }

    /**
     * Merges whichever commit is outstanding: an inbound one previously seen through
     * [processMessage], otherwise the locally staged one. Nothing staged → deterministic error
     * (the port's two-phase contract; raw openmls would silently no-op).
     */
    override suspend fun mergePendingCommit(groupId: GroupId) {
        val inbound = store.takeInbound(groupId.value)
        store.mutate { state ->
            val updated = if (inbound != null) {
                mlsMergeStagedCommit(state, groupId.toBytes(), inbound).state
            } else {
                mlsMergePendingCommit(state, groupId.toBytes()).state
            }
            updated to Unit
        }
    }

    override suspend fun processMessage(groupId: GroupId, message: ByteArray): ProcessedMessage {
        val processed = store.mutate { state ->
            val result = mlsProcessMessage(state, groupId.toBytes(), message)
            result.state to result
        }
        return when (processed.kind) {
            ProcessedKind.APPLICATION -> ProcessedMessage.ApplicationMessage(processed.payload)
            ProcessedKind.STAGED_COMMIT -> {
                // Keep the bytes: the openmls StagedCommit cannot cross the FFI boundary, so the
                // subsequent mergePendingCommit replays them.
                store.stageInbound(groupId.value, processed.payload)
                ProcessedMessage.StagedCommit(Commit(processed.payload))
            }
            ProcessedKind.PROPOSAL -> ProcessedMessage.Proposal(processed.payload)
        }
    }

    /**
     * Join the group a [welcome] invites us to, returning its MLS group id.
     *
     * NOT part of [GroupPort]: the domain has no join verb yet — pairing owns that flow (TASK-67,
     * docs/architecture/crypto-pairing.md). It lives here so a second device can actually enter a
     * group, which is what the roundtrip / forward-secrecy tests exercise.
     */
    suspend fun joinFromWelcome(welcome: ByteArray): GroupId =
        store.mutate { state ->
            val result = mlsJoinFromWelcome(state, welcome)
            result.state to GroupId(result.groupId.decodeToString())
        }
}

internal fun GroupId.toBytes(): ByteArray = value.encodeToByteArray()
