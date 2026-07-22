package family.pairing.api

data class DeviceSigningKeyPair(
    val publicKey: SigningPublicKey,
    val privateKey: SigningPrivateKey,
)
