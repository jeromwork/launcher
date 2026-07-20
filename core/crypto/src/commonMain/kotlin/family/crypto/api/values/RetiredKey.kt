package family.crypto.api.values

import kotlinx.datetime.Instant

/** Stub-only domain type for future rotation spec (TBD) — see ADR-008. */
data class RetiredKey(
    val keyId: KeyId,
    val retiredAt: Instant,
    val reason: RotationReason,
    val replacedBy: KeyId?
)
