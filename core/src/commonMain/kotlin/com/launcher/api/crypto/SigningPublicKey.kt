package com.launcher.api.crypto

import kotlinx.serialization.Serializable

@Serializable
class SigningPublicKey(val bytes: ByteArray) {
    init { require(bytes.size == 32) { "Ed25519 public key must be 32 bytes, got ${bytes.size}" } }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SigningPublicKey) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}
