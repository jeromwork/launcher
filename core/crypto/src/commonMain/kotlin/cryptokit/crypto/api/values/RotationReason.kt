package cryptokit.crypto.api.values

/** Stub-only domain type for future rotation spec (TBD) — see ADR-008. */
sealed class RotationReason {
    object Periodic : RotationReason()
    object SuspectedCompromise : RotationReason()
    object DeviceChange : RotationReason()
    data class Custom(val reason: String) : RotationReason()
}
