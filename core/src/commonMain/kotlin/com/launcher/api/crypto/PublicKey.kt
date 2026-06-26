package com.launcher.api.crypto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("PublicKey")
class PublicKey(val bytes: ByteArray) {
    init { require(bytes.size == 32) { "X25519 public key must be 32 bytes, got ${bytes.size}" } }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKey) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}
