package family.crypto.ports

/**
 * Domain port for MLS group lifecycle (TASK-123). Shape mirrors Wire `CoreCrypto`/`Conversation`
 * two-phase commit (FR-009) so the future real openmls adapter (TASK-124) is a thin wrapper and
 * the contract tests are not rewritten (SC-005).
 *
 * Two-phase model: a mutating call ([addMembers], [removeMembers], [selfUpdate],
 * [commitToPendingProposals]) STAGES a change and returns a [CommitBundle] to fan out; the local
 * epoch only advances once [mergePendingCommit] is called. This matches openmls' pending-commit
 * semantics and lets the caller abort a commit that the server rejects.
 *
 * NOT a port method: authorization (who-may-add / who-may-remove) — that is application policy
 * (ML6, docs/architecture/crypto-pairing.md, TASK-102), never a crypto-primitive concern.
 *
 * All methods `suspend`: openmls operations are blocking; the real adapter wraps them in
 * `Dispatchers.IO`. Keeping the port suspend now avoids a breaking change later (rule 4).
 */
interface GroupPort {

    /** Create a brand-new single-member group under [groupId]. Double-create is an error. */
    suspend fun createGroup(groupId: GroupId)

    /** Stage adding [keyPackages] to [groupId]; returns the commit + Welcome to fan out. */
    suspend fun addMembers(groupId: GroupId, keyPackages: List<KeyPackage>): CommitBundle

    /** Stage removing the members identified by [members] from [groupId]. */
    suspend fun removeMembers(groupId: GroupId, members: List<IdentityKey>): CommitBundle

    /** Stage a self-update (rotate own leaf key) in [groupId] for post-compromise security. */
    suspend fun selfUpdate(groupId: GroupId): CommitBundle

    /**
     * Stage a commit covering all currently pending proposals in [groupId]. Returns `null` when
     * there are no pending proposals to commit.
     */
    suspend fun commitToPendingProposals(groupId: GroupId): CommitBundle?

    /** Merge the previously staged pending commit for [groupId], advancing the local epoch. */
    suspend fun mergePendingCommit(groupId: GroupId)

    /**
     * Process an inbound MLS message ([message] opaque bytes) for [groupId], returning the
     * decoded [ProcessedMessage] variant (application message / staged commit / proposal).
     */
    suspend fun processMessage(groupId: GroupId, message: ByteArray): ProcessedMessage
}
