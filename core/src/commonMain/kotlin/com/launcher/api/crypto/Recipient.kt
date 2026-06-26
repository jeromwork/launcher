package com.launcher.api.crypto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Recipient")
class Recipient(
    val deviceId: DeviceId,
    val sealedCEK: ByteArray,
) {
    init {
        require(sealedCEK.size == SEALED_CEK_SIZE) {
            "sealedCEK must be $SEALED_CEK_SIZE bytes (X25519 ephemeral pub 32 + sealed CEK 32 + MAC 16), got ${sealedCEK.size}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Recipient) return false
        return deviceId == other.deviceId && sealedCEK.contentEquals(other.sealedCEK)
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + sealedCEK.contentHashCode()
        return result
    }
}
