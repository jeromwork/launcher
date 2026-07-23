package family.crypto.ports

/**
 * Domain port for the KeyPackage pool (TASK-123) — the async-add primitive of X3DH/MLS.
 *
 * A member publishes a batch of one-time KeyPackages (plus, optionally, a reusable last-resort
 * key) so others can add them to a group while they are offline. [claim] pops exactly one package
 * for a target identity; when the pool is exhausted it returns [ClaimResult.Empty] (never throws),
 * or falls back to the last-resort key if one is published (FR-011, RFC 9750).
 *
 * [localCount] drives client-side refill: when it drops below the client's refill threshold the
 * client publishes a fresh batch. The threshold itself is application policy, not a port concern.
 *
 * MUST NOT import `KeyVault` (`:core:keys`) — signing material reaches openmls via
 * `KeyVault.exportDerivedKey(MLS_SIGNATURE, …)` INSIDE the real adapter (TASK-124), never as a
 * port parameter (FR-008, docs/architecture/crypto-key-hierarchy.md).
 */
interface KeyPackagePort {

    /**
     * Publish [keyPackages] into the caller's pool. When [isLastResort] is true the batch is
     * treated as the reusable fallback key (RFC 9750 §5.1) rather than one-time packages.
     */
    suspend fun publish(keyPackages: List<KeyPackage>, isLastResort: Boolean)

    /** Claim one KeyPackage for [target]; [ClaimResult.Empty] when the pool is exhausted. */
    suspend fun claim(target: IdentityKey): ClaimResult

    /** Number of one-time KeyPackages remaining in the local pool (drives refill). */
    suspend fun localCount(): Int
}
