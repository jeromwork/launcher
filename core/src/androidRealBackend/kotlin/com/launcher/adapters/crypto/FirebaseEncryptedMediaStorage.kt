package com.launcher.adapters.crypto

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.EncryptedEnvelope
import com.launcher.api.crypto.EncryptedMediaStorage
import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

// Firebase Storage adapter для /links/{linkId}/private-media/{uuid}.
// Object content — CBOR-serialized EncryptedEnvelope. Размерный лимит 500 KB
// enforce'ится Storage Rules (контракт + defence-in-depth).
@OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)
class FirebaseEncryptedMediaStorage(
    private val storage: FirebaseStorage,
    private val cbor: Cbor = Cbor { ignoreUnknownKeys = true },
) : EncryptedMediaStorage {

    override suspend fun upload(
        linkId: String,
        uuid: Uuid,
        envelope: EncryptedEnvelope,
    ): Outcome<Unit, CryptoError> {
        return try {
            val bytes = cbor.encodeToByteArray(envelope)
            blobRef(linkId, uuid).putBytes(bytes).await()
            Outcome.Success(Unit)
        } catch (e: Throwable) {
            Outcome.Failure(CryptoError.StorageFailure(e))
        }
    }

    override suspend fun download(
        linkId: String,
        uuid: Uuid,
    ): Outcome<EncryptedEnvelope, CryptoError> {
        val bytes = try {
            blobRef(linkId, uuid).getBytes(MAX_DOWNLOAD_BYTES).await()
        } catch (e: StorageException) {
            return if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                Outcome.Failure(CryptoError.BlobMissing(uuid))
            } else {
                Outcome.Failure(CryptoError.StorageFailure(e))
            }
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.StorageFailure(e))
        }
        val env = try {
            cbor.decodeFromByteArray<EncryptedEnvelope>(bytes)
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.MalformedEnvelope(uuid = uuid, cause = e))
        }
        return Outcome.Success(env)
    }

    override suspend fun delete(linkId: String, uuid: Uuid): Outcome<Unit, CryptoError> {
        return try {
            blobRef(linkId, uuid).delete().await()
            Outcome.Success(Unit)
        } catch (e: StorageException) {
            // delete idempotent: 404 → success.
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(CryptoError.StorageFailure(e))
            }
        } catch (e: Throwable) {
            Outcome.Failure(CryptoError.StorageFailure(e))
        }
    }

    override suspend fun exists(linkId: String, uuid: Uuid): Boolean {
        return try {
            blobRef(linkId, uuid).metadata.await()
            true
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun list(linkId: String): List<Uuid> {
        return try {
            val result = storage.reference
                .child("links").child(linkId).child(PATH_PRIVATE_MEDIA)
                .listAll().await()
            result.items.mapNotNull { itemRef ->
                runCatching { Uuid.parse(itemRef.name) }.getOrNull()
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun blobRef(linkId: String, uuid: Uuid) =
        storage.reference
            .child("links").child(linkId)
            .child(PATH_PRIVATE_MEDIA).child(uuid.toString())

    private companion object {
        private const val PATH_PRIVATE_MEDIA = "private-media"
        private const val MAX_DOWNLOAD_BYTES = 512L * 1024  // 512 KB safety margin > 500 KB cap
    }
}
