package com.launcher.api.crypto

import com.launcher.api.result.Outcome

/**
 * Android Keystore-backed port. Adapter (Phase 3) использует:
 *   - X25519 priv: AES-wrap (Keystore не поддерживает X25519 нативно)
 *   - Ed25519 priv: native Keystore API 31+, AES-wrap fallback API 30
 *
 * ⚠️ **DO NOT use directly from UI / business logic / feature modules.**
 * Use `PrivateMediaUploader` / `PrivateMediaResolver` facades (spec 012) instead.
 * See [docs/dev/private-media-architecture.md].
 *
 * Rationale: Article XI §8 (Reuse before invention).
 */
interface SecureKeystore {
    fun generateAndStoreEncryption(alias: String): DeviceKeyPair

    fun generateAndStoreSigning(alias: String): DeviceSigningKeyPair

    fun loadEncryption(alias: String): Outcome<DeviceKeyPair, CryptoError>

    fun loadSigning(alias: String): Outcome<DeviceSigningKeyPair, CryptoError>

    fun delete(alias: String)

    fun exists(alias: String): Boolean
}
