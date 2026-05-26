package com.launcher.adapters.media

import com.launcher.api.crypto.AeadCipher
import com.launcher.api.crypto.AsymmetricCrypto
import com.launcher.api.crypto.CIPHER_SUITE_ID_V1
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceId
import com.launcher.api.crypto.DeviceKeyPair
import com.launcher.api.crypto.EncryptedEnvelope
import com.launcher.api.crypto.EncryptedMediaStorage
import com.launcher.api.media.LocalMediaStore
import com.launcher.api.media.PrivateMediaKind
import com.launcher.api.media.PrivateMediaResolution
import com.launcher.api.media.PrivateMediaResolver
import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Spec 012 facade implementation. Pipeline для read-side (resolve "private:<uuid>"):
 *  1. Parse iconRef → uuid (regex check).
 *  2. [LocalMediaStore.read] — if hit, return bytes immediately (≤100ms, FR-004).
 *  3. Miss → [EncryptedMediaStorage.download] envelope.
 *  4. Find own recipient в envelope.recipients (constant-time search в adapter).
 *  5. Unseal CEK с own keypair (через AsymmetricCrypto.unsealCEK).
 *  6. Recompose combined ciphertext = body + mac.
 *  7. Decrypt с AAD = canonical metadata (через AeadCipher.decrypt).
 *  8. Detect kind via metadata.kind:
 *     - Image → plaintext IS bytes.
 *     - Document → decode DocumentPayload, extract bytes + label.
 *  9. [LocalMediaStore.write] decrypted bytes для повторного instant-show.
 *  10. Return [PrivateMediaResolution.Bytes(bytes, kind)].
 *
 * Любая CryptoError → [PrivateMediaResolution.Failed] с categorised reason
 * (для admin indicator hints FR-022).
 *
 * NO logging of plaintext / CEK / label bytes (per Spec 011 fitness test T112).
 *
 * Task: T1218 (Phase 3). FR-002, FR-021.
 */
@OptIn(ExperimentalUuidApi::class)
class PrivateMediaResolverImpl(
    private val aeadCipher: AeadCipher,
    private val asymmetricCrypto: AsymmetricCrypto,
    private val storage: EncryptedMediaStorage,
    private val localStore: LocalMediaStore,
    private val ownDeviceId: suspend () -> DeviceId,
    private val ownKeyPair: suspend () -> Outcome<DeviceKeyPair, CryptoError>,
    private val canonicalAad: (Map<String, ByteArray>) -> ByteArray = ::canonicalMetadataAadDefault,
) : PrivateMediaResolver {

    override suspend fun resolve(
        privateIconRef: String,
        linkId: String,
    ): PrivateMediaResolution {
        // 1. Parse iconRef.
        val uuid = parseUuid(privateIconRef)
            ?: return PrivateMediaResolution.Failed(
                PrivateMediaResolution.FailureReason.InvalidRef
            )

        // 2. LocalMediaStore hit?
        val cached = localStore.read(uuid.toString())
        if (cached != null) {
            val bytes = cached.readBytes()
            // kind метаdata теряется в local store (мы храним plaintext bytes напрямую).
            // Image vs Document определяется через структуру bytes — но caller обычно знает
            // из context (плитка контакта vs DocumentTile). Возвращаем kind=null если из
            // local-store; caller fallback'ает на свой контекст.
            return PrivateMediaResolution.Bytes(bytes = bytes, kind = null)
        }

        // 3. Download.
        val downloadResult = storage.download(linkId, uuid)
        val envelope = when (downloadResult) {
            is Outcome.Success -> downloadResult.value
            is Outcome.Failure -> return categoriseFailure(downloadResult.error)
        }

        // Cipher suite check.
        if (envelope.cipherSuiteId != CIPHER_SUITE_ID_V1) {
            return PrivateMediaResolution.Failed(
                PrivateMediaResolution.FailureReason.MacFailed,
                CryptoError.CipherSuiteUnsupported(envelope.cipherSuiteId),
            )
        }

        // 4. Find own recipient.
        val ownId = ownDeviceId()
        val ownRecipient = envelope.recipients.firstOrNull { it.deviceId == ownId }
            ?: return PrivateMediaResolution.Failed(
                PrivateMediaResolution.FailureReason.RecipientNotFound,
                CryptoError.RecipientNotFound(ownId),
            )

        // 5. Unseal CEK.
        val keyPairResult = ownKeyPair()
        val keyPair = when (keyPairResult) {
            is Outcome.Success -> keyPairResult.value
            is Outcome.Failure -> return categoriseFailure(keyPairResult.error)
        }
        val cekResult = asymmetricCrypto.unsealCEK(ownRecipient.sealedCEK, keyPair)
        val cek = when (cekResult) {
            is Outcome.Success -> cekResult.value
            is Outcome.Failure -> return categoriseFailure(cekResult.error)
        }

        // 6-7. Decrypt с AAD = canonical metadata.
        // Per Spec 012 wire-format decision (см. PrivateMediaUploaderImpl): combined
        // bytes хранятся opaque в envelope.ciphertext; envelope.mac — placeholder,
        // не используется здесь. Mac verification — внутри AeadCipher.decrypt.
        val aad = canonicalAad(envelope.metadata)
        val plaintextResult = cek.use { activeCek ->
            aeadCipher.decrypt(envelope.ciphertext, activeCek, envelope.nonce, aad)
        }
        val plaintext = when (plaintextResult) {
            is Outcome.Success -> plaintextResult.value
            is Outcome.Failure -> return categoriseFailure(plaintextResult.error, uuid)
        }

        // 8. Interpret payload by kind.
        val kindWire = envelope.metadata["kind"]?.decodeToString()
        val kind = PrivateMediaKind.fromWireOrNull(kindWire)
        val (imageBytes, _) = when (kind) {
            PrivateMediaKind.Document -> {
                val payload = DocumentPayloadCodec.decode(plaintext)
                if (payload != null) {
                    val img = DocumentPayloadCodec.decodeImageBytes(plaintext)
                        ?: return PrivateMediaResolution.Failed(
                            PrivateMediaResolution.FailureReason.MacFailed
                        )
                    img to payload.label
                } else {
                    // Legacy / mis-tagged — treat plaintext as raw bytes.
                    plaintext to null
                }
            }
            else -> plaintext to null
        }

        // 9. Cache in local store.
        localStore.write(uuid.toString(), imageBytes)

        // 10. Return.
        return PrivateMediaResolution.Bytes(bytes = imageBytes, kind = kind)
    }

    private fun parseUuid(iconRef: String): Uuid? {
        if (!iconRef.startsWith(NAMESPACE_PREFIX)) return null
        val uuidString = iconRef.removePrefix(NAMESPACE_PREFIX)
        return try {
            Uuid.parse(uuidString)
        } catch (e: Throwable) {
            null
        }
    }

    private fun categoriseFailure(
        error: CryptoError,
        @Suppress("UNUSED_PARAMETER") uuid: Uuid? = null,
    ): PrivateMediaResolution.Failed = when (error) {
        is CryptoError.BlobMissing -> PrivateMediaResolution.Failed(
            PrivateMediaResolution.FailureReason.BlobMissing, error
        )
        is CryptoError.MacFailed,
        is CryptoError.MalformedEnvelope,
        is CryptoError.CipherSuiteUnsupported -> PrivateMediaResolution.Failed(
            PrivateMediaResolution.FailureReason.MacFailed, error
        )
        is CryptoError.RecipientNotFound -> PrivateMediaResolution.Failed(
            PrivateMediaResolution.FailureReason.RecipientNotFound, error
        )
        is CryptoError.KeyNotFound,
        is CryptoError.KeystoreFailure,
        is CryptoError.SignatureVerifyFailed -> PrivateMediaResolution.Failed(
            PrivateMediaResolution.FailureReason.KeyNotFound, error
        )
        is CryptoError.StorageFailure -> PrivateMediaResolution.Failed(
            PrivateMediaResolution.FailureReason.NetworkError, error
        )
    }

    companion object {
        const val NAMESPACE_PREFIX: String = "private:"
    }
}

/** Default canonical AAD — mirrors [PrivateMediaUploaderImpl.canonicalMetadataAad]. */
internal fun canonicalMetadataAadDefault(metadata: Map<String, ByteArray>): ByteArray {
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
