package family.crypto.stubs

import family.crypto.api.KeyRotation
import family.crypto.api.values.KeyId
import family.crypto.api.values.KeyNamespace
import family.crypto.api.values.RetiredKey
import family.crypto.api.values.RotationReason

/**
 * Interface-only stub per FR-011. Real implementation in a future rotation spec (TBD,
 * number assigned at /speckit.specify time) — see ADR-008.
 *
 * Safe defaults:
 *  • [currentKeyId] — throws because there is no canonical "current" key yet.
 *  • [keyHistory] — empty list (no rotation history).
 * Mutating operations throw `NotImplementedError` with a clear cross-reference.
 */
class StubKeyRotation : KeyRotation {

    override fun currentKeyId(purpose: KeyNamespace): KeyId =
        throw NotImplementedError(
            "KeyRotation.currentKeyId real-impl deferred to future spec (TBD) — see ADR-008"
        )

    override fun keyHistory(purpose: KeyNamespace): List<RetiredKey> = emptyList()

    override suspend fun rotateIdentityKey(purpose: KeyNamespace, reason: RotationReason): KeyId =
        throw NotImplementedError(
            "KeyRotation.rotateIdentityKey real-impl deferred to future spec (TBD) — see ADR-008"
        )

    override suspend fun revoke(keyId: KeyId, reason: RotationReason): Unit =
        throw NotImplementedError(
            "KeyRotation.revoke real-impl deferred to future spec (TBD) — see ADR-008"
        )
}
