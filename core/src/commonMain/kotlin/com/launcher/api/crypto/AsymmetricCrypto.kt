package com.launcher.api.crypto

import com.launcher.api.result.Outcome

// X25519 asymmetric crypto port. Hybrid encryption — sealCEK уплотняет CEK для
// recipient'а через crypto_box_seal. Constant-time recipient search live в adapter
// (T056) — НЕ early-return при первом MAC match.
interface AsymmetricCrypto {
    fun generateX25519Pair(alias: String): DeviceKeyPair

    fun sealCEK(cek: ContentEncryptionKey, recipientPub: PublicKey): ByteArray

    fun unsealCEK(
        sealedCEK: ByteArray,
        ownPair: DeviceKeyPair,
    ): Outcome<ContentEncryptionKey, CryptoError>
}
