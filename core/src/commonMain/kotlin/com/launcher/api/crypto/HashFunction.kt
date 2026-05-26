package com.launcher.api.crypto

/**
 * BLAKE2b-256 hash port. 011: logging Pub key fingerprint without exposing key.
 * Future spec 012: content-based dedup. Future TBD-Jitsi: safety numbers fingerprint.
 *
 * ⚠️ **DO NOT use directly from UI / business logic / feature modules.**
 * Use `PrivateMediaUploader` / `PrivateMediaResolver` facades (spec 012) instead.
 * See [docs/dev/private-media-architecture.md].
 *
 * Rationale: Article XI §8 (Reuse before invention).
 */
interface HashFunction {
    // Returns HASH_OUTPUT_SIZE bytes (32 для BLAKE2b-256).
    fun hash(data: ByteArray): ByteArray
}
