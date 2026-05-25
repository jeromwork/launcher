package com.launcher.api.crypto

// BLAKE2b-256 hash port. 011: logging Pub key fingerprint without exposing key.
// Future spec 012: content-based dedup. Future TBD-Jitsi: safety numbers fingerprint.
interface HashFunction {
    // Returns HASH_OUTPUT_SIZE bytes (32 для BLAKE2b-256).
    fun hash(data: ByteArray): ByteArray
}
