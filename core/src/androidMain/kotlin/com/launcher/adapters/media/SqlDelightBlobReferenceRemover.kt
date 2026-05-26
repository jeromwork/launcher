package com.launcher.adapters.media

import com.launcher.adapters.crypto.SqlDelightBlobReferenceLedger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Spec 012 — Android adapter implementing [BlobReferenceRemover] over existing
 * 011 [SqlDelightBlobReferenceLedger]. Mirrors [SqlDelightBlobReferenceWriter].
 *
 * После decrement: when [SqlDelightBlobReferenceLedger.countRefs] == 0, blob
 * eligible для cleanup by existing 011 BackgroundReconciler (which runs ≤
 * 5 минут cadence — SC-005).
 *
 * Task: T1249 (Phase 7).
 */
@OptIn(ExperimentalUuidApi::class)
class SqlDelightBlobReferenceRemover(
    private val ledger: SqlDelightBlobReferenceLedger,
) : BlobReferenceRemover {
    override suspend fun removeRef(uuid: Uuid, linkId: String, refSource: String) {
        withContext(Dispatchers.IO) {
            ledger.removeRef(uuid, linkId, refSource)
        }
    }
}
