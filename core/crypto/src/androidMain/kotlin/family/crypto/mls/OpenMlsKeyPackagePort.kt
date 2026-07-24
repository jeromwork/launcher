package family.crypto.mls

import family.crypto.ports.ClaimResult
import family.crypto.ports.IdentityKey
import family.crypto.ports.KeyPackage
import family.crypto.ports.KeyPackagePort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.crypto_ffi.mlsGenerateKeyPackage

/**
 * Real [KeyPackagePort] over openmls 0.8.1 — **local-only** (TASK-124, T013).
 *
 * The pool lives in this process: [publish] stores packages the caller generated, [claim] pops one
 * (falling back to the reusable last-resort key per RFC 9750 §5.1), [localCount] drives refill.
 * There is no server round trip — the KeyPackage directory endpoint is TASK-104, and until it
 * exists a client shares its own KeyPackage out-of-band (QR / pairing).
 *
 * TODO(server-roadmap): publishing / claiming must move to the KeyPackage directory endpoint
 * (TASK-104) for async add of an offline member — see docs/dev/server-roadmap.md.
 * TODO(task-112): the signing key behind a generated KeyPackage is ephemeral, generated in Rust;
 * bind it to `KeyVault.exportDerivedKey(MLS_SIGNATURE, …)` (docs/architecture/crypto-key-hierarchy.md).
 *
 * [claim] takes a `target` identity but ignores it: with a local-only pool there is exactly one
 * pool, the caller's own. Per-target pools arrive with the server directory.
 */
class OpenMlsKeyPackagePort internal constructor(
    private val store: MlsSnapshotStore,
) : KeyPackagePort {

    constructor() : this(MlsSnapshotStore())

    private val mutex = Mutex()
    private val oneTime = ArrayDeque<KeyPackage>()
    private var lastResort: KeyPackage? = null

    override suspend fun publish(keyPackages: List<KeyPackage>, isLastResort: Boolean) {
        mutex.withLock {
            if (isLastResort) {
                lastResort = keyPackages.lastOrNull() ?: lastResort
            } else {
                oneTime.addAll(keyPackages)
            }
        }
    }

    /** Never throws on an exhausted pool — [ClaimResult.Empty] is the documented outcome. */
    override suspend fun claim(target: IdentityKey): ClaimResult = mutex.withLock {
        val oneTimeKey = oneTime.removeFirstOrNull()
        when {
            oneTimeKey != null -> ClaimResult.Claimed(oneTimeKey, isLastResort = false)
            else -> lastResort
                ?.let { ClaimResult.Claimed(it, isLastResort = true) }
                ?: ClaimResult.Empty
        }
    }

    override suspend fun localCount(): Int = mutex.withLock { oneTime.size }

    /**
     * Generate a fresh KeyPackage bound to this device's MLS storage, together with the identity
     * (Ed25519 signature public key) it carries — the same value the group roster will report, and
     * therefore the handle a caller passes to `GroupPort.removeMembers`.
     *
     * Not a [KeyPackagePort] verb — the port publishes packages the caller already has. Generation
     * needs the openmls storage (the private init / encryption keys stay there so a later Welcome
     * can be opened), so it lives on the real adapter.
     */
    suspend fun generateWithIdentity(isLastResort: Boolean = false): GeneratedKeyPackage =
        store.mutate { state ->
            val result = mlsGenerateKeyPackage(state, isLastResort)
            result.state to GeneratedKeyPackage(
                keyPackage = KeyPackage(result.keyPackage),
                identity = IdentityKey(result.identity),
            )
        }

    /** [generateWithIdentity] when the caller only needs the package itself. */
    suspend fun generate(isLastResort: Boolean = false): KeyPackage =
        generateWithIdentity(isLastResort).keyPackage
}

/** A freshly generated KeyPackage and the member identity bound to it. */
data class GeneratedKeyPackage(
    val keyPackage: KeyPackage,
    val identity: IdentityKey,
)
