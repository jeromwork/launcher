package com.launcher.fake.crypto

import family.pairing.api.EncryptedMediaStorage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// mockBackend flavor — in-memory blob store. Production: WorkerEncryptedMediaStorage.
//
// TASK-141 — the upload/download/exists surface (and EncryptedEnvelope) was
// removed as dead. Only delete + list remain (used by revoke() cleanup). With no
// producer, this fake holds nothing: list is empty and delete is a no-op, which
// is the correct behaviour for a mock backend that never stored a blob.
@OptIn(ExperimentalUuidApi::class)
class InMemoryEncryptedMediaStorage : EncryptedMediaStorage {
    override suspend fun delete(linkId: String, uuid: Uuid) {
        // no-op: nothing is ever stored in the mock backend
    }

    override suspend fun list(linkId: String): List<Uuid> = emptyList()
}
