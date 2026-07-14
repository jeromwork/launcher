package cryptokit.crypto.api

import cryptokit.crypto.api.values.KeyPair
import cryptokit.crypto.api.values.SealedBlob
import cryptokit.crypto.api.values.SharedSecret
import cryptokit.crypto.api.values.Signature

/**
 * X25519 (ECDH) + Ed25519 (signatures) + sealed-box envelope. Per FR-007.
 *
 * Sealed-box (`sealForRecipient` / `openSealed`) provides anonymous encryption to a
 * recipient's X25519 public key — used for ADR-008 social recovery.
 */
interface AsymmetricCrypto {

    /** Generate a new X25519 keypair (for ECDH key agreement). */
    suspend fun generateX25519KeyPair(): KeyPair

    /** Generate a new Ed25519 keypair (for digital signatures). */
    suspend fun generateEd25519KeyPair(): KeyPair

    /**
     * Deterministic Ed25519 keypair derived from a 32-byte seed (libsodium
     * `crypto_sign_seed_keypair`). Used by TASK-112 [cryptokit.keys.api.vault.KeyVault] to derive
     * the identity keypair from `root_key`.
     *
     * Same seed → same keypair, bit-for-bit, across platforms.
     */
    suspend fun ed25519KeyPairFromSeed(seed: ByteArray): KeyPair

    /**
     * X25519 ECDH key agreement.
     * @param myPrivate 32-byte X25519 private key.
     * @param theirPublic 32-byte X25519 public key from peer.
     * @return 32-byte shared secret (feed into [KeyDerivation]).
     * @throws cryptokit.crypto.exception.CryptoException.InvalidPublicKey on low-order/malformed.
     */
    suspend fun deriveSharedSecret(myPrivate: ByteArray, theirPublic: ByteArray): SharedSecret

    /** Sign [message] with Ed25519 [privateKey]. Returns 64-byte detached signature. */
    suspend fun sign(message: ByteArray, privateKey: ByteArray): Signature

    /**
     * Verify Ed25519 [signature]. Returns true if valid, false otherwise.
     * Never throws on invalid signature — invalid-signature is a regular control-flow result.
     */
    suspend fun verify(signature: Signature, message: ByteArray, publicKey: ByteArray): Boolean

    /**
     * Seal a small payload for [recipientPublicKey] using libsodium sealed-box.
     * Sender is anonymous (ephemeral keypair used internally).
     *
     * @param payload typically 16-32 bytes (e.g., a CEK to be sealed for a peer).
     * @return sealed blob (48 + payload.size bytes: ephemeralPub(32) + ciphertext + mac(16)).
     */
    suspend fun sealForRecipient(payload: ByteArray, recipientPublicKey: ByteArray): SealedBlob

    /**
     * Open a sealed blob with [recipientPrivateKey].
     * @throws cryptokit.crypto.exception.CryptoException.DecryptionFailed if not addressed
     *   to this recipient or blob is tampered.
     */
    suspend fun openSealed(blob: SealedBlob, recipientPrivateKey: ByteArray): ByteArray
}
