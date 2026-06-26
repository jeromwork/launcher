package cryptokit.crypto.stubs

import cryptokit.crypto.api.KeyRotation
import cryptokit.crypto.api.values.KeyId
import cryptokit.crypto.api.values.KeyNamespace
import cryptokit.crypto.api.values.RetiredKey
import cryptokit.crypto.api.values.RotationReason

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
