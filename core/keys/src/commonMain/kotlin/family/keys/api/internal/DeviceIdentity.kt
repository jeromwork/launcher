package family.keys.api.internal

import family.keys.api.DeviceId

/**
 * Internal port: this device's stable identity for envelope encryption.
 *
 * Allocated on first launch after sign-in:
 *  - Generate X25519 keypair via [family.crypto.api.AsymmetricCrypto.generateX25519KeyPair].
 *  - Wrap private key under Android Keystore via [family.crypto.api.SecureKeyStore].
 *  - Allocate stable [DeviceId] (random ULID-like string) and persist in DataStore.
 *  - Publish public key into [PublicKeyDirectory] under owner's namespace.
 *
 * Subsequent app starts: load existing [DeviceId] + unwrap private key.
 *
 * Reinstall = new identity. There is no recovery of per-device private key from
 * cloud (it never leaves Keystore). The user re-pairs / receives new grants if
 * needed. Their root key (F-5 [family.keys.api.RootKeyManager]) is independently
 * recovered via passphrase — separate concern.
 */
interface DeviceIdentity {

    /** This device's stable ID within the signed-in user's namespace. */
    suspend fun thisDeviceId(): DeviceId

    /** This device's X25519 public key — published into [PublicKeyDirectory]. */
    suspend fun myPubKey(): ByteArray

    /**
     * This device's X25519 private key, unwrapped from Keystore. Caller must
     * zeroize via `.fill(0)` after use (memory hygiene G-1).
     */
    suspend fun myPrivKey(): ByteArray
}
