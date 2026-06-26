package cryptokit.pairing.api

data class DeviceSigningKeyPair(
    val publicKey: SigningPublicKey,
    val privateKey: SigningPrivateKey,
)
