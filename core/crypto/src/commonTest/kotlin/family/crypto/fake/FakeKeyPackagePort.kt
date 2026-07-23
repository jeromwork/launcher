package family.crypto.fake

import family.crypto.ports.ClaimResult
import family.crypto.ports.IdentityKey
import family.crypto.ports.KeyPackage
import family.crypto.ports.KeyPackagePort

/**
 * In-memory [KeyPackagePort] fake (TASK-123, FR-003, FR-011). Models the async-add pool:
 *  - one-time KeyPackages are consumed exactly once by [claim] (atomic pop);
 *  - an exhausted pool falls back to the reusable last-resort key if one was published;
 *  - a fully empty pool (no last resort) returns [ClaimResult.Empty] — it never throws;
 *  - [localCount] reports only the one-time packages, so a consumer can trigger a refill.
 *
 * [claim]'s `target` is ignored — a single fake instance represents one identity's pool.
 */
class FakeKeyPackagePort : KeyPackagePort {

    private val pool = ArrayDeque<KeyPackage>()
    private var lastResort: KeyPackage? = null

    override suspend fun publish(keyPackages: List<KeyPackage>, isLastResort: Boolean) {
        if (isLastResort) {
            lastResort = keyPackages.firstOrNull() ?: lastResort
        } else {
            pool.addAll(keyPackages)
        }
    }

    override suspend fun claim(target: IdentityKey): ClaimResult = when {
        pool.isNotEmpty() -> ClaimResult.Claimed(pool.removeFirst(), isLastResort = false)
        lastResort != null -> ClaimResult.Claimed(lastResort!!, isLastResort = true)
        else -> ClaimResult.Empty
    }

    override suspend fun localCount(): Int = pool.size
}
