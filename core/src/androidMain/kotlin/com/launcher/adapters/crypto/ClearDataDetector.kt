package com.launcher.adapters.crypto

import com.launcher.adapters.crypto.db.CryptoStore

// Spec 011 research.md §5c + CHK-FR-015 — clear-data sentinel.
// При startup проверяем, есть ли row "clearDataAt" в SystemMeta:
//   - нет → fresh DB (app data clear / fresh install) → пишем clearDataAt = now,
//           возвращаем reconciliation grace period.
//   - есть → existing DB, return age.
//
// BackgroundReconciler skip'ит cleanup пока возраст < 7 дней — защита от
// случайной потери refs после clear-data (FR-015 grace period).
internal class ClearDataDetector(
    db: CryptoStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val q = db.systemMetaQueries

    fun ensureSentinel(): Long {
        val existing = q.get(KEY_CLEAR_DATA_AT).executeAsOneOrNull()
        return if (existing == null) {
            val now = nowMillis()
            q.put(KEY_CLEAR_DATA_AT, now.toString())
            now
        } else {
            existing.toLongOrNull() ?: run {
                val now = nowMillis()
                q.put(KEY_CLEAR_DATA_AT, now.toString())
                now
            }
        }
    }

    fun ageMillis(): Long {
        val ts = ensureSentinel()
        return nowMillis() - ts
    }

    fun isWithinGracePeriod(): Boolean = ageMillis() < GRACE_PERIOD_MILLIS

    companion object {
        const val KEY_CLEAR_DATA_AT = "clearDataAt"
        const val GRACE_PERIOD_MILLIS = 7L * 24 * 60 * 60 * 1000  // 7 days
    }
}
