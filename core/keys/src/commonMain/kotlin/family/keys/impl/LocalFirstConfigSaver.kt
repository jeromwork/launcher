package family.keys.impl

import family.keys.api.AsyncConfigPushQueue
import family.keys.api.ConfigSaver
import family.keys.api.IdentityProof
import family.keys.api.Outcome
import family.keys.api.QueueError
import family.keys.api.RemoteStorage
import family.keys.api.StorageError

/**
 * Local-first [ConfigSaver]: stages the payload into [AsyncConfigPushQueue] so
 * the UI gets an immediate success response, with the cloud push retried in
 * the background by [AsyncConfigPushQueue] implementation.
 *
 * Caller does not see the difference vs synchronous [RemoteStorageConfigSaver]
 * (same return type). The distinction is in the failure surface:
 *  - Synchronous saver returns Network / Unauthorized at call time.
 *  - This saver returns Success immediately on staging; eventual upload
 *    failure surfaces only via UI status indicator (queried separately).
 *
 * Reads (`loadOwn`, `loadForOther`, `listOwn`, `listForOther`) bypass the
 * queue and go straight to [RemoteStorage] — there's no staging for reads.
 */
class LocalFirstConfigSaver(
    private val storage: RemoteStorage,
    private val identity: IdentityProof,
    private val pushQueue: AsyncConfigPushQueue
) : ConfigSaver {

    override suspend fun saveOwn(
        configName: String,
        bytes: ByteArray
    ): Outcome<Unit, StorageError> {
        val uid = currentUid() ?: return Outcome.Failure(StorageError.NoIdentity)
        return enqueue(uid, ConfigSaver.keyOf(configName), bytes)
    }

    override suspend fun loadOwn(configName: String): Outcome<ByteArray, StorageError> {
        val uid = currentUid() ?: return Outcome.Failure(StorageError.NoIdentity)
        return storage.get(uid, ConfigSaver.keyOf(configName))
    }

    override suspend fun listOwn(): Outcome<List<String>, StorageError> {
        val uid = currentUid() ?: return Outcome.Failure(StorageError.NoIdentity)
        return when (val r = storage.list(uid, ConfigSaver.KEY_PREFIX_CONFIG)) {
            is Outcome.Success -> Outcome.Success(r.value.mapNotNull { ConfigSaver.configNameOf(it) })
            is Outcome.Failure -> Outcome.Failure(r.error)
        }
    }

    override suspend fun deleteOwn(configName: String): Outcome<Unit, StorageError> {
        val uid = currentUid() ?: return Outcome.Failure(StorageError.NoIdentity)
        // Delete is synchronous — deferred delete would risk stale data on
        // another device pulling before the queue runs.
        return storage.delete(uid, ConfigSaver.keyOf(configName))
    }

    override suspend fun saveForOther(
        ownerUid: String,
        configName: String,
        bytes: ByteArray
    ): Outcome<Unit, StorageError> = enqueue(ownerUid, ConfigSaver.keyOf(configName), bytes)

    override suspend fun loadForOther(
        ownerUid: String,
        configName: String
    ): Outcome<ByteArray, StorageError> = storage.get(ownerUid, ConfigSaver.keyOf(configName))

    override suspend fun listForOther(ownerUid: String): Outcome<List<String>, StorageError> =
        when (val r = storage.list(ownerUid, ConfigSaver.KEY_PREFIX_CONFIG)) {
            is Outcome.Success -> Outcome.Success(r.value.mapNotNull { ConfigSaver.configNameOf(it) })
            is Outcome.Failure -> Outcome.Failure(r.error)
        }

    private suspend fun enqueue(
        namespace: String,
        key: String,
        bytes: ByteArray
    ): Outcome<Unit, StorageError> {
        if (bytes.size > RemoteStorage.MAX_ENTRY_BYTES) {
            return Outcome.Failure(StorageError.TooLarge)
        }
        return when (val r = pushQueue.enqueue(namespace, key, bytes)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> Outcome.Failure(mapQueueError(r.error))
        }
    }

    private fun mapQueueError(e: QueueError): StorageError = when (e) {
        QueueError.TooLarge -> StorageError.TooLarge
        is QueueError.StagingFailure -> StorageError.Malformed("staging: ${e.message}")
        is QueueError.SchedulingFailure -> StorageError.Network()
    }

    private suspend fun currentUid(): String? =
        identity.currentIdentity()?.stableId?.takeIf { it.isNotEmpty() }
}
