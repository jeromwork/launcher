package com.launcher.fake.crypto

import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.EncryptedEnvelope
import com.launcher.api.crypto.EncryptedMediaStorage
import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FakeEncryptedMediaStorage : EncryptedMediaStorage {

    private data class Key(val linkId: String, val uuid: Uuid)
    private val store = mutableMapOf<Key, EncryptedEnvelope>()

    override suspend fun upload(
        linkId: String,
        uuid: Uuid,
        envelope: EncryptedEnvelope,
    ): Outcome<Unit, CryptoError> {
        store[Key(linkId, uuid)] = envelope
        return Outcome.Success(Unit)
    }

    override suspend fun download(linkId: String, uuid: Uuid): Outcome<EncryptedEnvelope, CryptoError> {
        val env = store[Key(linkId, uuid)] ?: return Outcome.Failure(CryptoError.BlobMissing(uuid))
        return Outcome.Success(env)
    }

    override suspend fun delete(linkId: String, uuid: Uuid): Outcome<Unit, CryptoError> {
        store.remove(Key(linkId, uuid))
        return Outcome.Success(Unit)
    }

    override suspend fun exists(linkId: String, uuid: Uuid): Boolean =
        store.containsKey(Key(linkId, uuid))

    override suspend fun list(linkId: String): List<Uuid> =
        store.keys.filter { it.linkId == linkId }.map { it.uuid }
}
