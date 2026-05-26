package com.launcher.api.crypto

import com.launcher.api.result.Outcome

// Android Keystore-backed port. Adapter (Phase 3) использует:
//   - X25519 priv: AES-wrap (Keystore не поддерживает X25519 нативно)
//   - Ed25519 priv: native Keystore API 31+, AES-wrap fallback API 30
interface SecureKeystore {
    fun generateAndStoreEncryption(alias: String): DeviceKeyPair

    fun generateAndStoreSigning(alias: String): DeviceSigningKeyPair

    fun loadEncryption(alias: String): Outcome<DeviceKeyPair, CryptoError>

    fun loadSigning(alias: String): Outcome<DeviceSigningKeyPair, CryptoError>

    fun delete(alias: String)

    fun exists(alias: String): Boolean
}
