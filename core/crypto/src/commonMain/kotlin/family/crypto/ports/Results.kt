package family.crypto.ports

/**
 * Composite / result types returned by the crypto ports (TASK-123).
 *
 * `sealed` results force callers to handle every branch (empty pool, commit-vs-application-message)
 * at compile time — no silent success (spec §Edge Cases). Like the value types, these carry NO
 * `@Serializable` and expose only opaque bytes; group state is never returned (FR-007).
 */

/**
 * Result of `addMembers` / `removeMembers` / `selfUpdate` / `commitToPendingProposals`.
 *
 * Deliberately NOT `GroupState` (FR-007) — the domain never receives the group's private state.
 * `welcome` is present only when the commit adds members (the Welcome message routed to the new
 * members out-of-band); it is `null` for member-removal / self-update commits.
 */
data class CommitBundle(
    val commit: Commit,
    val welcome: ByteArray? = null,
) {
    // value semantics over the ByteArray so contract tests can assert equality.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommitBundle) return false
        if (commit != other.commit) return false
        if (welcome != null) {
            if (other.welcome == null) return false
            if (!welcome.contentEquals(other.welcome)) return false
        } else if (other.welcome != null) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = commit.hashCode()
        result = 31 * result + (welcome?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Result of `GroupPort.processMessage` — mirrors openmls' `ProcessedMessage` shape.
 * The caller must branch on the variant: an application message carries plaintext to display,
 * a staged commit must be merged, a proposal must be surfaced to the group's commit policy.
 */
sealed interface ProcessedMessage {
    /** A decrypted application message — [plaintext] is ready for the consumer. */
    data class ApplicationMessage(val plaintext: ByteArray) : ProcessedMessage {
        override fun equals(other: Any?): Boolean =
            this === other || (other is ApplicationMessage && plaintext.contentEquals(other.plaintext))

        override fun hashCode(): Int = plaintext.contentHashCode()
    }

    /** A staged group change — the caller merges it via `mergePendingCommit`. */
    data class StagedCommit(val commit: Commit) : ProcessedMessage

    /** A raw proposal to be evaluated by the group's commit policy (application-level). */
    data class Proposal(val raw: ByteArray) : ProcessedMessage {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Proposal && raw.contentEquals(other.raw))

        override fun hashCode(): Int = raw.contentHashCode()
    }
}

/**
 * Result of `KeyPackagePort.claim`. An empty pool returns [Empty] — it never throws
 * (spec §Edge Cases). [Claimed.isLastResort] tells the caller the claimed package is the reusable
 * last-resort key (RFC 9750), so the client can trigger a refill (FR-011).
 */
sealed interface ClaimResult {
    data class Claimed(val keyPackage: KeyPackage, val isLastResort: Boolean) : ClaimResult
    data object Empty : ClaimResult
}
