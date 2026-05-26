package com.launcher.api.crypto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceIdentity(
    val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
    val deviceId: DeviceId,
    val publicKey: PublicKey,
    val signingPublicKey: SigningPublicKey,
    val signedTimestamp: Long,
    val signature: ByteArray,
    val createdAt: Long,
) {
    init {
        require(signature.size == ED25519_SIGNATURE_SIZE) {
            "Ed25519 signature must be $ED25519_SIGNATURE_SIZE bytes, got ${signature.size}"
        }
    }

    // Canonical byte representation of signed payload — фиксированный порядок полей
    // (deviceId | publicKey | signingPublicKey | signedTimestamp). Deterministic
    // — без CBOR map ordering ambiguity. Используется и при sign, и при verify.
    fun signedPayloadBytes(): ByteArray {
        val deviceIdBytes = deviceId.value.encodeToByteArray()
        val tsBytes = ByteArray(8)
        var ts = signedTimestamp
        for (i in 7 downTo 0) {
            tsBytes[i] = (ts and 0xFF).toByte()
            ts = ts ushr 8
        }
        val out = ByteArray(deviceIdBytes.size + publicKey.bytes.size + signingPublicKey.bytes.size + tsBytes.size)
        var off = 0
        deviceIdBytes.copyInto(out, off); off += deviceIdBytes.size
        publicKey.bytes.copyInto(out, off); off += publicKey.bytes.size
        signingPublicKey.bytes.copyInto(out, off); off += signingPublicKey.bytes.size
        tsBytes.copyInto(out, off)
        return out
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceIdentity) return false
        return schemaVersion == other.schemaVersion &&
            deviceId == other.deviceId &&
            publicKey == other.publicKey &&
            signingPublicKey == other.signingPublicKey &&
            signedTimestamp == other.signedTimestamp &&
            signature.contentEquals(other.signature) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + signingPublicKey.hashCode()
        result = 31 * result + signedTimestamp.hashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
