package com.launcher.adapters.media

import com.launcher.api.crypto.AeadCipher
import com.launcher.api.crypto.AsymmetricCrypto
import com.launcher.api.crypto.CIPHER_SUITE_ID_V1
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceIdentity
import com.launcher.api.crypto.EncryptedEnvelope
import com.launcher.api.crypto.EncryptedMediaStorage
import com.launcher.api.crypto.POLY1305_MAC_SIZE
import com.launcher.api.crypto.Recipient
import com.launcher.api.crypto.RecipientResolver
import com.launcher.api.crypto.SUPPORTED_SCHEMA_VERSION
import com.launcher.api.crypto.use
import com.launcher.api.media.PrivateMediaKind
import com.launcher.api.media.PrivateMediaUploader
import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Spec 012 facade implementation. Pure-Kotlin orchestration of 011 ports.
 *
 * Pipeline (per FR-001 / FR-006):
 *  1. Resolve recipients для linkId (через RecipientResolver).
 *  2. If kind = Document AND label != null → wrap (bytes, label) в [DocumentPayload] JSON;
 *     иначе use raw bytes. **Label NEVER в metadata** — privacy invariant.
 *  3. Generate CEK + nonce (через AeadCipher).
 *  4. Encrypt payload с CEK + nonce + AAD (canonical metadata bytes).
 *  5. Split combined ciphertext → ciphertext body + mac (last POLY1305_MAC_SIZE bytes).
 *  6. Seal CEK для каждого recipient (через AsymmetricCrypto.sealCEK).
 *  7. Build [EncryptedEnvelope] + upload (через EncryptedMediaStorage).
 *  8. Register reference в BlobReferenceLedger (через [BlobReferenceWriter]).
 *  9. Return "private:<uuid>".
 *
 * NO logging of plaintext / CEK / label bytes (per Spec 011 fitness test T112).
 *
 * Task: T1217 (Phase 3). FR-001, FR-006.
 */
@OptIn(ExperimentalUuidApi::class)
class PrivateMediaUploaderImpl(
    private val aeadCipher: AeadCipher,
    private val asymmetricCrypto: AsymmetricCrypto,
    private val storage: EncryptedMediaStorage,
    private val recipientResolver: RecipientResolver,
    private val ledger: BlobReferenceWriter,
    private val uuidGenerator: () -> Uuid = { Uuid.random() },
    private val clock: () -> Long = { 0L /* DI provides real clock */ },
) : PrivateMediaUploader {

    override suspend fun upload(
        bytes: ByteArray,
        kind: PrivateMediaKind,
        linkId: String,
        labelInsideCiphertext: String?,
        refSource: String,
    ): Outcome<String, CryptoError> {
        val recipients = try {
            recipientResolver.resolveRecipients(linkId)
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.StorageFailure(e))
        }
        if (recipients.isEmpty()) {
            return Outcome.Failure(
                // Synthetic placeholder UUID-format DeviceId — caller's deviceId is empty
                // recipient list (no peer); error category is RecipientNotFound regardless.
                CryptoError.RecipientNotFound(
                    com.launcher.api.crypto.DeviceId("00000000-0000-4000-8000-000000000000")
                )
            )
        }

        val plaintext: ByteArray = when {
            kind == PrivateMediaKind.Document && labelInsideCiphertext != null ->
                DocumentPayloadCodec.encode(bytes, labelInsideCiphertext)
            else -> bytes
        }

        val metadata = mapOf("kind" to kind.wireValue.encodeToByteArray())
        val aad = canonicalMetadataAad(metadata)
        val nonce = aeadCipher.randomNonce()
        val cek = aeadCipher.generateCEK()

        val sealedResult: SealedAndCiphertext = cek.use { activeCek ->
            val combined = aeadCipher.encrypt(plaintext, activeCek, nonce, aad)
            if (combined.size < POLY1305_MAC_SIZE) {
                return@use SealedAndCiphertext.Malformed
            }
            // Spec 012 wire-format decision: store full combined output (ciphertext || mac
            // OR mac || ciphertext per cipher suite) в `EncryptedEnvelope.ciphertext` field
            // как opaque. The separate `mac` field of the wire-format DTO is a placeholder
            // (zero-filled POLY1305_MAC_SIZE bytes) — actual MAC verification is done в
            // [AeadCipher.decrypt] which expects unchanged combined bytes.
            //
            // Rationale: keeping mac/ciphertext concatenation opaque inside the [ciphertext]
            // field avoids depending on a specific combined-mode layout (prepended vs appended
            // tag) which varies between libsodium IETF mode and other AEAD ciphers.
            val combinedAsCiphertext = combined
            val placeholderMac = ByteArray(POLY1305_MAC_SIZE)
            val sealed = recipients.map { recipient ->
                Recipient(
                    deviceId = recipient.deviceId,
                    sealedCEK = asymmetricCrypto.sealCEK(activeCek, recipient.publicKey),
                )
            }
            SealedAndCiphertext.Ok(combinedAsCiphertext, placeholderMac, sealed)
        }

        val (ciphertextOnly, mac, sealedRecipients) = when (sealedResult) {
            is SealedAndCiphertext.Malformed -> return Outcome.Failure(CryptoError.MalformedEnvelope())
            is SealedAndCiphertext.Ok -> Triple(sealedResult.ciphertext, sealedResult.mac, sealedResult.recipients)
        }

        val uuid = uuidGenerator()
        val envelope = try {
            EncryptedEnvelope(
                schemaVersion = SUPPORTED_SCHEMA_VERSION,
                cipherSuiteId = CIPHER_SUITE_ID_V1,
                nonce = nonce,
                recipients = sealedRecipients,
                ciphertext = ciphertextOnly,
                mac = mac,
                metadata = metadata,
            )
        } catch (e: IllegalArgumentException) {
            return Outcome.Failure(CryptoError.MalformedEnvelope(uuid = uuid, cause = e))
        }

        val uploadResult = storage.upload(linkId, uuid, envelope)
        if (uploadResult is Outcome.Failure) {
            return Outcome.Failure(uploadResult.error)
        }

        ledger.addRef(
            uuid = uuid,
            linkId = linkId,
            refSource = refSource,
            refUpdatedAt = clock(),
        )

        return Outcome.Success("private:$uuid")
    }

    /**
     * Canonical AAD encoding для metadata map. Deterministic ordering — keys sorted
     * lexicographically, values byte-concatenated с null-byte separators.
     *
     * Простая schema, не CBOR. MUST использоваться также в Decoder для verify.
     */
    internal fun canonicalMetadataAad(metadata: Map<String, ByteArray>): ByteArray {
        if (metadata.isEmpty()) return ByteArray(0)
        val sortedKeys = metadata.keys.sorted()
        var totalSize = 0
        for (k in sortedKeys) {
            totalSize += k.encodeToByteArray().size + 1 + metadata[k]!!.size + 1
        }
        val out = ByteArray(totalSize)
        var off = 0
        for (k in sortedKeys) {
            val kBytes = k.encodeToByteArray()
            kBytes.copyInto(out, off); off += kBytes.size
            out[off] = 0; off += 1
            val v = metadata[k]!!
            v.copyInto(out, off); off += v.size
            out[off] = 0; off += 1
        }
        return out
    }

    private sealed interface SealedAndCiphertext {
        data object Malformed : SealedAndCiphertext
        data class Ok(
            val ciphertext: ByteArray,
            val mac: ByteArray,
            val recipients: List<Recipient>,
        ) : SealedAndCiphertext
    }
}

/**
 * Spec 012 — narrow port над BlobReferenceLedger SQLDelight queries.
 * Adapter живёт в androidMain — wrapping generated query methods.
 * Defined здесь чтобы PrivateMediaUploaderImpl остался pure-commonMain.
 */
@OptIn(ExperimentalUuidApi::class)
fun interface BlobReferenceWriter {
    suspend fun addRef(uuid: Uuid, linkId: String, refSource: String, refUpdatedAt: Long)
}
