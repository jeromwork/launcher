package com.launcher.app.data.recovery

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cryptokit.keys.api.PassphraseAttemptCounter
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed [PassphraseAttemptCounter] (T122e-T122i, H-1 mitigation, FR-027).
 *
 * Persistent — переживает app kill / ViewModel recreate, что блокирует тривиальный
 * bypass через relaunch. Auto-reset через [resetTimeoutMillis] (default 1 час)
 * чтобы залоченный user не оказался заблокирован навсегда.
 *
 * **Storage**: `datastore/passphrase_attempts_v1.preferences_pb` под app-private dir.
 * Excluded из cloud-backup (data_extraction_rules.xml) — иначе restore из
 * Google Drive backup сбрасывает counter без physical device access.
 *
 * **Accepted residual risk** (T122i): user может Clear App Data → counter сбросится
 * локально. Но также cleared local root key cache, и attacker'у придётся пройти
 * полный recovery cycle заново. Не идеал, но baseline.
 *
 * **Keys per uid**: `attempts_${uid}` (Int) + `last_attempt_${uid}` (Long ms).
 *
 * TODO(server-roadmap SRV-RATELIMIT-001): после переезда на свой сервер заменим
 * на server-side counter с tied lockout — Clear App Data перестанет бы bypass'ить.
 */
class DataStorePassphraseAttemptCounter(
    private val context: Context,
    private val resetTimeoutMillis: Long = 3_600_000L,
    private val now: () -> Long = { System.currentTimeMillis() }
) : PassphraseAttemptCounter {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    override suspend fun currentCount(uid: String): Int {
        val prefs = context.dataStore.data.first()
        return prefs[attemptsKey(uid)] ?: 0
    }

    override suspend fun recordFailedAttempt(uid: String): Int {
        var newCount = 0
        context.dataStore.edit { prefs ->
            val cur = prefs[attemptsKey(uid)] ?: 0
            newCount = cur + 1
            prefs[attemptsKey(uid)] = newCount
            prefs[lastAttemptKey(uid)] = now()
        }
        return newCount
    }

    override suspend fun resetIfExpired(uid: String) {
        val prefs = context.dataStore.data.first()
        val last = prefs[lastAttemptKey(uid)] ?: return
        if (now() - last > resetTimeoutMillis) {
            context.dataStore.edit {
                it.remove(attemptsKey(uid))
                it.remove(lastAttemptKey(uid))
            }
        }
    }

    override suspend fun clear(uid: String) {
        context.dataStore.edit {
            it.remove(attemptsKey(uid))
            it.remove(lastAttemptKey(uid))
        }
    }

    private fun attemptsKey(uid: String): Preferences.Key<Int> =
        intPreferencesKey("attempts_$uid")

    private fun lastAttemptKey(uid: String): Preferences.Key<Long> =
        longPreferencesKey("last_attempt_$uid")

    companion object {
        const val DATASTORE_NAME: String = "passphrase_attempts_v1"
    }
}
