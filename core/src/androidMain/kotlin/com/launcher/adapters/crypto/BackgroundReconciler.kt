package com.launcher.adapters.crypto

import cryptokit.crypto.exception.CryptoException
import cryptokit.pairing.api.EncryptedMediaStorage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException

// Spec 011 FR-042 + research.md §5/§5c — orphan blob reconciliation.
// Periodic 24h cadence (real scheduling — WorkManager periodic worker,
// тривиально интегрируется потребителем; здесь — чистая reconcile logic).
//
// Algorithm:
//   1. Если ClearDataDetector.isWithinGracePeriod() → skip (защита от случайной
//      потери refs после clear-data, FR-015).
//   2. Получить полный список Storage blobs для link'а.
//   3. Сравнить с BlobReferenceLedger. Удалить orphans (Storage has, ledger does not).
//   4. Удалить из ledger записи для blobs, которые не в Storage (consistency).
@OptIn(ExperimentalUuidApi::class)
class BackgroundReconciler(
    private val storage: EncryptedMediaStorage,
    private val ledger: SqlDelightBlobReferenceLedger,
    private val clearData: ClearDataDetector,
) {
    suspend fun reconcile(linkId: String): ReconcileResult {
        if (clearData.isWithinGracePeriod()) {
            return ReconcileResult.Skipped(reason = "clear-data grace period")
        }
        val storageUuids = storage.list(linkId).toSet()
        val ledgerUuids = ledger.listUuids(linkId).toSet()

        // Orphans в Storage (нет refs в ledger) → удалить.
        val orphans = storageUuids - ledgerUuids
        var deleted = 0
        var failed = 0
        for (uuid in orphans) {
            try {
                storage.delete(linkId, uuid)
                deleted++
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: CryptoException) {
                failed++
            } catch (_: Throwable) {
                failed++
            }
        }

        // Stale ledger entries (blob уже удалён из Storage) → cleanup ledger.
        val staleLedger = ledgerUuids - storageUuids
        for (uuid in staleLedger) {
            ledger.removeAllForBlob(uuid, linkId)
        }

        return ReconcileResult.Done(orphansDeleted = deleted, deleteFailures = failed, ledgerCleaned = staleLedger.size)
    }

    sealed interface ReconcileResult {
        data class Skipped(val reason: String) : ReconcileResult
        data class Done(val orphansDeleted: Int, val deleteFailures: Int, val ledgerCleaned: Int) : ReconcileResult
    }
}
