package com.launcher.fake.crypto

import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceKeyPair
import com.launcher.api.crypto.DeviceSigningKeyPair
import com.launcher.api.crypto.SecureKeystore
import com.launcher.api.result.Outcome

class FakeSecureKeystore(
    private val asymm: FakeAsymmetricCrypto = FakeAsymmetricCrypto(),
    private val sign: FakeDigitalSignature = FakeDigitalSignature(),
) : SecureKeystore {

    private val encryption = mutableMapOf<String, DeviceKeyPair>()
    private val signing = mutableMapOf<String, DeviceSigningKeyPair>()

    override fun generateAndStoreEncryption(alias: String): DeviceKeyPair {
        val kp = asymm.generateX25519Pair(alias)
        encryption[alias] = kp
        return kp
    }

    override fun generateAndStoreSigning(alias: String): DeviceSigningKeyPair {
        val kp = sign.generateEd25519Pair(alias)
        signing[alias] = kp
        return kp
    }

    override fun loadEncryption(alias: String): Outcome<DeviceKeyPair, CryptoError> {
        val kp = encryption[alias] ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        return Outcome.Success(kp)
    }

    override fun loadSigning(alias: String): Outcome<DeviceSigningKeyPair, CryptoError> {
        val kp = signing[alias] ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        return Outcome.Success(kp)
    }

    override fun delete(alias: String) {
        encryption.remove(alias)
        signing.remove(alias)
    }

    override fun exists(alias: String): Boolean =
        encryption.containsKey(alias) || signing.containsKey(alias)
}
