package cryptokit.keys.api.internal

import cryptokit.keys.api.CipherError
import cryptokit.keys.api.DeviceId
import cryptokit.keys.api.Envelope
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecipientPubKey

/**
 * Internal hybrid-envelope cipher per spec 011 §C-3.
 *
 * Not exposed to caller-side code (app modules); reachable only inside
 * [cryptokit.keys.api.RemoteStorage] implementation. App code interacts via
 * `RemoteStorage.put / get`; the cipher itself is invisible.
 *
 * Replaces the legacy single-recipient symmetric [cryptokit.keys.api.ConfigCipher]
 * which only worked for self-edit under a shared root key. The envelope form
 * supports N=1..M recipients with identical wire format and code path; multi-
 * recipient activation is a property of [RecipientResolver], not of this port.
 */
interface ConfigCipher2 {

    /**
     * Seal [plaintext] under N recipient public keys. Internally:
     *  1. Generate fresh random CEK (32 bytes).
     *  2. Encrypt plaintext with CEK + AEAD (XChaCha20-Poly1305) + [aad].
     *  3. For each recipient: seal CEK under their X25519 pub key via
     *     `crypto_box_seal`. Store as [Envelope.recipientKeys] map keyed by
     *     [DeviceId].
     *  4. Zeroize CEK.
     *
     * Caller passes pre-computed [aad] (typically `namespace || key || schemaVersion`).
     */
    suspend fun seal(
        plaintext: ByteArray,
        recipients: List<RecipientPubKey>,
        aad: ByteArray
    ): Outcome<Envelope, CipherError>

    /**
     * Open [envelope] using this device's private key. Internally:
     *  1. Locate `envelope.recipientKeys[myDeviceId.value]`. If absent →
     *     [CipherError.NotARecipient].
     *  2. Validate `envelope.aad == aad` (context-confusion defence).
     *  3. Validate `envelope.schemaVersion` and `envelope.algorithm` are
     *     supported (defence-in-depth: refuse forged future-version blobs
     *     without touching the AEAD layer).
     *  4. Open sealed CEK with `crypto_box_seal_open` using [myPrivKey].
     *  5. Decrypt ciphertext with CEK + AEAD + [aad].
     *  6. Zeroize CEK.
     */
    suspend fun open(
        envelope: Envelope,
        myPrivKey: ByteArray,
        myDeviceId: DeviceId,
        aad: ByteArray
    ): Outcome<ByteArray, CipherError>
}
