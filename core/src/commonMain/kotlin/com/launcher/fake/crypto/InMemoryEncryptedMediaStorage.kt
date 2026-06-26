package com.launcher.fake.crypto

import cryptokit.crypto.exception.CryptoException
import cryptokit.pairing.api.EncryptedEnvelope
import cryptokit.pairing.api.EncryptedMediaStorage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// mockBackend flavor — in-memory blob store. Production: WorkerEncryptedMediaStorage.
//
// TASK-51 Phase 6 — Outcome<T, CryptoError> → throws CryptoException.
@OptIn(ExperimentalUuidApi::class)
class InMemoryEncryptedMediaStorage : EncryptedMediaStorage {
    private data class Key(val linkId: String, val uuid: Uuid)
    private val store = mutableMapOf<Key, EncryptedEnvelope>()

    override suspend fun upload(linkId: String, uuid: Uuid, envelope: EncryptedEnvelope) {
        store[Key(linkId, uuid)] = envelope
    }

    override suspend fun download(linkId: String, uuid: Uuid): EncryptedEnvelope {
        return store[Key(linkId, uuid)]
            ?: throw CryptoException.SerializationException("blob missing: $uuid")
    }

    override suspend fun delete(linkId: String, uuid: Uuid) {
        store.remove(Key(linkId, uuid))
    }

    override suspend fun exists(linkId: String, uuid: Uuid): Boolean =
        store.containsKey(Key(linkId, uuid))

    override suspend fun list(linkId: String): List<Uuid> =
        store.keys.filter { it.linkId == linkId }.map { it.uuid }
}
