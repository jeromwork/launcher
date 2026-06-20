package family.keys.impl

import family.keys.api.ConfigSaver
import family.keys.api.IdentityProof
import family.keys.api.Outcome
import family.keys.api.RemoteStorage
import family.keys.api.StorageError

/**
 * [ConfigSaver] implementation that delegates to [RemoteStorage].
 *
 * Reads the current UID from [IdentityProof] for own-config operations.
 * Cross-user operations pass [ownerUid] directly to [RemoteStorage] —
 * Firestore Security Rules + [family.keys.api.internal.RecipientResolver]
 * enforce access at the backend.
 */
class RemoteStorageConfigSaver(
    private val storage: RemoteStorage,
    private val identity: IdentityProof
) : ConfigSaver {

    override suspend fun saveOwn(
        configName: String,
        bytes: ByteArray
    ): Outcome<Unit, StorageError> {
        val uid = currentUid() ?: return Outcome.Failure(StorageError.NoIdentity)
        return storage.put(uid, ConfigSaver.keyOf(configName), bytes)
    }

    override suspend fun loadOwn(configName: String): Outcome<ByteArray, StorageError> {
        val uid = currentUid() ?: return Outcome.Failure(StorageError.NoIdentity)
        return storage.get(uid, ConfigSaver.keyOf(configName))
    }

    override suspend fun listOwn(): Outcome<List<String>, StorageError> {
        val uid = currentUid() ?: return Outcome.Failure(StorageError.NoIdentity)
        return listConfigsIn(uid)
    }

    override suspend fun deleteOwn(configName: String): Outcome<Unit, StorageError> {
        val uid = currentUid() ?: return Outcome.Failure(StorageError.NoIdentity)
        return storage.delete(uid, ConfigSaver.keyOf(configName))
    }

    override suspend fun saveForOther(
        ownerUid: String,
        configName: String,
        bytes: ByteArray
    ): Outcome<Unit, StorageError> = storage.put(ownerUid, ConfigSaver.keyOf(configName), bytes)

    override suspend fun loadForOther(
        ownerUid: String,
        configName: String
    ): Outcome<ByteArray, StorageError> = storage.get(ownerUid, ConfigSaver.keyOf(configName))

    override suspend fun listForOther(ownerUid: String): Outcome<List<String>, StorageError> =
        listConfigsIn(ownerUid)

    private suspend fun listConfigsIn(namespace: String): Outcome<List<String>, StorageError> =
        when (val r = storage.list(namespace, ConfigSaver.KEY_PREFIX_CONFIG)) {
            is Outcome.Success -> Outcome.Success(r.value.mapNotNull { ConfigSaver.configNameOf(it) })
            is Outcome.Failure -> Outcome.Failure(r.error)
        }

    private suspend fun currentUid(): String? = identity.currentIdentity()?.stableId?.takeIf { it.isNotEmpty() }
}
