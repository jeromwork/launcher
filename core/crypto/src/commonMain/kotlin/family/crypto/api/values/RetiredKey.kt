package family.crypto.api.values

import kotlinx.datetime.Instant

/** Stub-only domain type for spec 017. */
data class RetiredKey(
    val keyId: KeyId,
    val retiredAt: Instant,
    val reason: RotationReason,
    val replacedBy: KeyId?
)
