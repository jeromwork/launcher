package com.launcher.adapters.media

import com.launcher.adapters.crypto.SqlDelightBlobReferenceLedger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Spec 012 — [BlobReferenceWriter] adapter wrapping existing 011
 * [SqlDelightBlobReferenceLedger]. Bridges the narrow facade port (commonMain)
 * to the full SQLDelight-backed ledger (androidMain).
 *
 * Task: T1230 (Phase 4). FR-023.
 */
@OptIn(ExperimentalUuidApi::class)
class SqlDelightBlobReferenceWriter(
    private val ledger: SqlDelightBlobReferenceLedger,
) : BlobReferenceWriter {
    override suspend fun addRef(uuid: Uuid, linkId: String, refSource: String, refUpdatedAt: Long) {
        // SQLDelight ledger.addRef uses its own nowMillis() — refUpdatedAt parameter
        // accepted by spec 012 port is currently ignored (delegated to ledger clock).
        // If override needed for tests, ledger constructor accepts a custom clock.
        withContext(Dispatchers.IO) {
            ledger.addRef(uuid, linkId, refSource)
        }
    }
}
