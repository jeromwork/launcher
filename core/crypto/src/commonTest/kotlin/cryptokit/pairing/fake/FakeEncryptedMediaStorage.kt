package cryptokit.pairing.fake

import cryptokit.crypto.exception.CryptoException
import cryptokit.pairing.api.EncryptedEnvelope
import cryptokit.pairing.api.EncryptedMediaStorage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * TEST-ONLY in-memory [EncryptedMediaStorage] — CLAUDE.md §6 mock-first.
 *
 * Production: WorkerEncryptedMediaStorage (realBackend). This fake does no
 * size limit enforcement — that contract lives in Storage Rules, not on client.
 */
@OptIn(ExperimentalUuidApi::class)
class FakeEncryptedMediaStorage : EncryptedMediaStorage {

    private data class Key(val linkId: String, val uuid: Uuid)
    private val store: MutableMap<Key, EncryptedEnvelope> = mutableMapOf()

    var failUploadWith: CryptoException? = null
    var failDownloadWith: CryptoException? = null

    override suspend fun upload(linkId: String, uuid: Uuid, envelope: EncryptedEnvelope) {
        failUploadWith?.let { ex -> failUploadWith = null; throw ex }
        store[Key(linkId, uuid)] = envelope
    }

    override suspend fun download(linkId: String, uuid: Uuid): EncryptedEnvelope {
        failDownloadWith?.let { ex -> failDownloadWith = null; throw ex }
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

    fun clear() {
        store.clear()
        failUploadWith = null
        failDownloadWith = null
    }
}
