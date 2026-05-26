package com.launcher.api.crypto

data class DeviceSigningKeyPair(
    val publicKey: SigningPublicKey,
    val privateKey: SigningPrivateKey,
)
