package family.crypto.api.values

/** Stub-only domain type for spec 017 (multi-device-recovery). */
sealed class RotationReason {
    object Periodic : RotationReason()
    object SuspectedCompromise : RotationReason()
    object DeviceChange : RotationReason()
    data class Custom(val reason: String) : RotationReason()
}
