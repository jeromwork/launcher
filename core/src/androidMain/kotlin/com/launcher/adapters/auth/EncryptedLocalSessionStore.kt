package com.launcher.adapters.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.launcher.api.auth.internal.SessionRecord
import com.launcher.api.auth.internal.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Production [SessionStore] на базе EncryptedSharedPreferences с TEE-backed
 * AES-256-GCM master ключом из Android Keystore.
 *
 *  - File: `auth_session_v1.preferences.xml` в app sandbox.
 *  - Key: единственный ключ `session` → JSON-сериализованный [SessionRecord].
 *  - Initial restore — async через [scope], не блокирует cold start (FR-035).
 *  - Corrupted blob → `current()` возвращает `null` через `runCatching`
 *    (FR-023, защита от migration v1→v2 mismatched read).
 *
 * Живёт в `:core/androidMain/adapters/auth/`, рядом с [com.launcher.api.auth.internal.SessionStore]
 * port'ом, чтобы видеть его `internal` visibility (CLAUDE.md §6 — adapter
 * в том же модуле, что и port).
 *
 * Per spec 017 FR-020, FR-021, FR-023, FR-035, contract `session-record-v1.md`.
 *
 * TODO(F-CRYPTO migration): currently EncryptedSharedPreferences (L0
 * tamper-resistance level). Когда F-5 ConfigCipher ship'нется, мигрируем к
 * `SecureKeystore` из spec 016 (F-CRYPTO) — additive change через keystore alias.
 * Per FR-020 + research.md §R5.
 *
 * TODO(authorized-request-signer): future port для подписи RPC будет читать
 * `SessionRecord.extra["firebase_jwt"]` через этот store. Consumer'ы НЕ видят
 * token напрямую (Detekt NoSessionRecordInConsumers enforces, T797).
 */
internal class EncryptedLocalSessionStore(
    context: Context,
    private val json: Json,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : SessionStore {

    private val masterKey: MasterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _changes = MutableStateFlow<SessionRecord?>(null)
    override val sessionChanges: Flow<SessionRecord?> = _changes.asStateFlow()

    init {
        // Async restore — не блокируем cold start. _changes начнётся с null
        // и переключится на восстановленную запись когда I/O закончится.
        scope.launch {
            _changes.value = readBlob()
        }
    }

    override suspend fun save(session: SessionRecord) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_SESSION, json.encodeToString(session))
                .apply()
        }
        _changes.value = session
    }

    override suspend fun current(): SessionRecord? = withContext(Dispatchers.IO) {
        readBlob()
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(KEY_SESSION).apply()
        }
        _changes.value = null
    }

    private fun readBlob(): SessionRecord? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { json.decodeFromString<SessionRecord>(raw) }
            .onFailure { Log.w(TAG, "session.corrupted: decode failed, returning null") }
            .getOrNull()
    }

    companion object {
        private const val TAG = "Auth"
        private const val PREFS_NAME = "auth_session_v1.preferences"
        private const val KEY_SESSION = "session"
    }
}
