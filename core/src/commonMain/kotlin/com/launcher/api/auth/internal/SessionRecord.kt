package com.launcher.api.auth.internal

import kotlinx.serialization.Serializable

/**
 * Внутренняя структура хранения сессии. **Не visible consumer'ам** —
 * Detekt-правило `NoSessionRecordInConsumers` (T797, Phase 8) запретит
 * импорт из любого модуля кроме `:core` internal + `app/auth/`.
 *
 * Хранится EncryptedLocalSessionStore (Phase 4) сериализованной как JSON
 * с `schemaVersion = 1`. Поле `extra` — escape-hatch для provider-specific
 * данных (сейчас: Firebase JWT под ключом `"firebase_jwt"`).
 *
 * Поля времени — epoch-millis Long, чтобы не тянуть kotlinx-datetime в :core
 * (остальной :core тоже использует Long, см. DocSnapshot, SqlDelightLocalConfigStore).
 *
 * Per spec 017 FR-013, FR-021, clarification Q2, contract `session-record-v1.md`.
 */
/**
 * Единственный источник номера версии этого формата
 * ([docs/architecture/wire-format.md](../../../../../../../../../docs/architecture/wire-format.md) §11).
 * Целочисленная форма — до конвертации в точечную строку (TASK-138).
 */
internal const val SESSION_RECORD_SCHEMA_VERSION: Int = 1

@Serializable
internal data class SessionRecord(
    val schemaVersion: Int = SESSION_RECORD_SCHEMA_VERSION,
    val stableId: String,
    val expiresAtEpochMillis: Long?,
    val refreshToken: String?,
    val extra: Map<String, String> = emptyMap(),
)
