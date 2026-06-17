package family.crypto.api

import family.crypto.api.values.KeyId
import family.crypto.api.values.KeyNamespace
import family.crypto.api.values.RetiredKey
import family.crypto.api.values.RotationReason

/**
 * Per FR-011 — interface declared in F-CRYPTO; real implementation deferred to spec 017
 * (multi-device-recovery, see ADR-008). [family.crypto.stubs.StubKeyRotation] is the
 * sole implementation in F-CRYPTO 1.0.0.
 */
interface KeyRotation {
    fun currentKeyId(purpose: KeyNamespace): KeyId
    fun keyHistory(purpose: KeyNamespace): List<RetiredKey>
    suspend fun rotateIdentityKey(purpose: KeyNamespace, reason: RotationReason): KeyId
    suspend fun revoke(keyId: KeyId, reason: RotationReason)
}
