package family.keys.impl

import family.crypto.api.AeadCipher
import family.crypto.api.AsymmetricCrypto
import family.crypto.api.RandomSource
import family.crypto.api.values.Ciphertext
import family.crypto.api.values.SealedBlob
import family.crypto.exception.CryptoException
import family.keys.api.CipherError
import family.keys.api.DeviceId
import family.keys.api.Envelope
import family.keys.api.Outcome
import family.keys.api.RecipientPubKey
import family.keys.api.internal.ConfigCipher2

/**
 * Hybrid-envelope cipher per spec 011 §C-3 — F-5b implementation.
 *
 * Workflow on [seal]:
 *  1. Generate one-time CEK (32 random bytes).
 *  2. AEAD-encrypt plaintext using CEK + caller-supplied AAD.
 *  3. For each recipient, seal CEK under their X25519 public key via
 *     libsodium `crypto_box_seal`.
 *  4. Build [Envelope] with map [DeviceId.value] → sealed CEK.
 *  5. Zeroize CEK.
 *
 * Workflow on [open]:
 *  1. Validate algorithm (H-3, defence-in-depth, before touching AEAD). Wire
 *     version is gated by the storage adapter, not here (TASK-141).
 *  2. Look up own [DeviceId] inside envelope.recipientKeys; if absent → NotARecipient.
 *  3. Validate AAD matches caller-recomputed value (context-confusion defence).
 *  4. Open sealed CEK with own private key.
 *  5. AEAD-decrypt ciphertext.
 *  6. Zeroize CEK.
 */
class EnvelopeConfigCipherImpl(
    private val aead: AeadCipher,
    private val asymmetric: AsymmetricCrypto,
    private val random: RandomSource
) : ConfigCipher2 {

    override suspend fun seal(
        plaintext: ByteArray,
        recipients: List<RecipientPubKey>,
        aad: ByteArray
    ): Outcome<Envelope, CipherError> {
        if (plaintext.size > MAX_PLAINTEXT_BYTES) {
            return Outcome.Failure(CipherError.ConfigTooLarge)
        }
        if (recipients.isEmpty()) {
            return Outcome.Failure(CipherError.InvalidInput(
                IllegalArgumentException("recipients must not be empty")
            ))
        }
        // Defence against duplicate routing slots: a malicious or buggy resolver
        // could pass two recipients with the same deviceId, last write wins,
        // earlier recipient becomes unable to decrypt. Reject explicitly.
        val ids = recipients.map { it.deviceId }
        if (ids.toSet().size != ids.size) {
            return Outcome.Failure(CipherError.InvalidInput(
                IllegalStateException("recipients contain duplicate deviceIds")
            ))
        }

        val cek = random.nextBytes(Envelope.CEK_SIZE)
        try {
            val ct: Ciphertext = aead.encrypt(plaintext, cek, aad)
            val nonce = ct.bytes.copyOfRange(0, Envelope.NONCE_SIZE)
            val cipherPart = ct.bytes.copyOfRange(Envelope.NONCE_SIZE, ct.bytes.size)

            val sealedPerRecipient = mutableMapOf<String, ByteArray>()
            for (recipient in recipients) {
                val sealed: SealedBlob = asymmetric.sealForRecipient(cek, recipient.pubKey)
                require(sealed.bytes.size == Envelope.SEALED_CEK_SIZE) {
                    "sealed CEK size ${sealed.bytes.size} != ${Envelope.SEALED_CEK_SIZE}"
                }
                sealedPerRecipient[recipient.deviceId.value] = sealed.bytes
            }

            return Outcome.Success(Envelope(
                ciphertext = cipherPart,
                nonce = nonce,
                aad = aad,
                recipientKeys = sealedPerRecipient
            ))
        } catch (t: Throwable) {
            return Outcome.Failure(CipherError.InvalidInput(t))
        } finally {
            cek.fill(0)
        }
    }

    override suspend fun open(
        envelope: Envelope,
        myPrivKey: ByteArray,
        myDeviceId: DeviceId,
        aad: ByteArray
    ): Outcome<ByteArray, CipherError> {
        // H-3 — refuse an unknown encryption algorithm BEFORE touching the crypto
        // layer. This is a cryptographic-parameter check, not a wire-version gate:
        // the version gate lives in the storage adapter now (TASK-141). Crypto
        // legitimately refuses an algorithm it cannot execute.
        if (envelope.algorithm != Envelope.ALGORITHM_V1) {
            return Outcome.Failure(CipherError.AlgorithmUnsupported)
        }
        if (envelope.nonce.size != Envelope.NONCE_SIZE) {
            return Outcome.Failure(CipherError.InvalidInput(
                IllegalArgumentException("nonce size ${envelope.nonce.size} != ${Envelope.NONCE_SIZE}")
            ))
        }
        if (!envelope.aad.contentEquals(aad)) {
            // Context confusion: AAD mismatch between stored and caller-recomputed.
            // Same surface as AEAD failure — do not reveal cause to attacker.
            return Outcome.Failure(CipherError.AeadAuthFailed)
        }
        val mySealedCek = envelope.recipientKeys[myDeviceId.value]
            ?: return Outcome.Failure(CipherError.NotARecipient)
        if (mySealedCek.size != Envelope.SEALED_CEK_SIZE) {
            return Outcome.Failure(CipherError.InvalidInput(
                IllegalArgumentException("sealed CEK size ${mySealedCek.size} != ${Envelope.SEALED_CEK_SIZE}")
            ))
        }

        var cek: ByteArray? = null
        try {
            cek = asymmetric.openSealed(SealedBlob(mySealedCek), myPrivKey)
            if (cek.size != Envelope.CEK_SIZE) {
                return Outcome.Failure(CipherError.InvalidInput(
                    IllegalArgumentException("recovered CEK size ${cek.size} != ${Envelope.CEK_SIZE}")
                ))
            }
            val envelopeBytes = envelope.nonce + envelope.ciphertext
            val plaintext = aead.decrypt(Ciphertext(envelopeBytes), cek, envelope.aad)
            return Outcome.Success(plaintext)
        } catch (e: CryptoException.DecryptionFailed) {
            return Outcome.Failure(CipherError.AeadAuthFailed)
        } catch (e: CryptoException.MalformedCiphertext) {
            return Outcome.Failure(CipherError.InvalidInput(e))
        } catch (t: Throwable) {
            return Outcome.Failure(CipherError.InvalidInput(t))
        } finally {
            cek?.fill(0)
        }
    }

    companion object {
        /** Spec 018 FR-029 — 256 KB plaintext limit. */
        const val MAX_PLAINTEXT_BYTES: Int = 256 * 1024
    }
}
