package com.launcher.api.auth.internal

import com.launcher.wire.WireVersion
import com.launcher.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

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
 * Version constants for this format
 * ([docs/architecture/wire-format.md](../../../../../../../../../docs/architecture/wire-format.md) §11).
 */
internal val SESSION_RECORD_SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

/** A session record is local to one device and never read by another build. */
internal val SESSION_RECORD_MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

/** Written wholesale by the device that owns it; no cross-writer merge exists. */
internal val SESSION_RECORD_MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)

// @EncodeDefault: this format encodes with `encodeDefaults = false`, and I1 requires the version
// fields on every document.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SessionRecord(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SESSION_RECORD_SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = SESSION_RECORD_MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = SESSION_RECORD_MIN_WRITER_VERSION,
    val stableId: String,
    val expiresAtEpochMillis: Long?,
    val refreshToken: String?,
    val extra: Map<String, String> = emptyMap(),
) : WireVersionHeader
