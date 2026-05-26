package com.launcher.adapters.crypto

import com.launcher.adapters.crypto.db.CryptoStore
import com.launcher.api.media.BlobReference
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// SQLDelight-backed BlobReferenceLedger wrapper. Domain-side semantics:
// addRef/removeRef + countRefs + deleteByLink. По refSource (string-enum)
// фронт sourced может различать config-current / history-slot-N / pending-draft.
@OptIn(ExperimentalUuidApi::class)
class SqlDelightBlobReferenceLedger(
    db: CryptoStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val q = db.blobReferenceLedgerQueries

    fun addRef(uuid: Uuid, linkId: String, refSource: String) {
        q.addRef(uuid.toString(), linkId, refSource, nowMillis())
    }

    fun removeRef(uuid: Uuid, linkId: String, refSource: String) {
        q.removeRef(uuid.toString(), linkId, refSource)
    }

    fun countRefs(uuid: Uuid, linkId: String): Long =
        q.countRefs(uuid.toString(), linkId).executeAsOne()

    fun listUuids(linkId: String): List<Uuid> =
        q.selectAllUuids(linkId).executeAsList()
            .mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }

    fun deleteByLink(linkId: String) {
        q.deleteByLink(linkId)
    }

    fun removeAllForBlob(uuid: Uuid, linkId: String) {
        q.removeAllForBlob(uuid.toString(), linkId)
    }
}
