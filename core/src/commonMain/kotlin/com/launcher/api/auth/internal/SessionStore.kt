package com.launcher.api.auth.internal

import kotlinx.coroutines.flow.Flow

/**
 * Внутренний порт хранилища сессии. Реализации:
 *  - `EncryptedLocalSessionStore` (app/androidMain, Phase 4) — реальная
 *    persistent сессия через EncryptedSharedPreferences + TEE-backed master key.
 *  - `FakeSessionStore` (commonTest, Phase 2) — in-memory HashMap, для тестов.
 *
 * **Не visible consumer'ам** — Detekt-правило `NoSessionRecordInConsumers`
 * (T797, Phase 8) запретит импорт за пределы `:core` internal + `app/auth/`.
 *
 * Per spec 017 FR-012, clarification Q2.
 */
internal interface SessionStore {
    suspend fun save(session: SessionRecord)
    suspend fun current(): SessionRecord?
    suspend fun clear()
    val sessionChanges: Flow<SessionRecord?>
}
