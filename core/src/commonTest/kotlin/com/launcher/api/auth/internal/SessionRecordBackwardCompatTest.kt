package com.launcher.api.auth.internal

import family.wire.WireVersion

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Backward-compatibility тест wire format [SessionRecord] v1.
 *
 *  - V1 фикстура читается полностью без потерь (5 полей).
 *  - V2 hypothetical фикстура корректно отвергается (schemaVersion != 1).
 *    Реальная защита от mismatched-version blob — на уровне
 *    EncryptedLocalSessionStore (Phase 4): `runCatching` + return null
 *    при decode failure (FR-023).
 *
 * Per spec 017 FR-022, FR-023, contract `session-record-v1.md`, CLAUDE.md §5.
 */
class SessionRecordBackwardCompatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun readsV1FixtureCorrectly() {
        val decoded = json.decodeFromString<SessionRecord>(SessionRecordFixtures.V1_CANONICAL)

        assertEquals(WireVersion(1, 0), decoded.schemaVersion)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", decoded.stableId)
        assertEquals(1739456789000L, decoded.expiresAtEpochMillis)
        assertEquals("1//04test-refresh-token-stable-fixture", decoded.refreshToken)
        assertEquals(
            mapOf("firebase_jwt" to "eyJhbGciOiJSUzI1NiIs.test.payload"),
            decoded.extra,
        )
    }

    @Test
    fun v2FixtureRejectedAtParseTime() {
        // V2 hypothetical имеет несовместимые поля (missing required `extra`,
        // missing required `refreshToken`/`expiresAtEpochMillis`).
        // Kotlinx serialization бросит MissingFieldException — это нормально:
        // EncryptedLocalSessionStore оборачивает decode в runCatching и
        // возвращает null (graceful failure per FR-023).
        assertFailsWith<Exception> {
            json.decodeFromString<SessionRecord>(SessionRecordFixtures.V2_HYPOTHETICAL)
        }
    }

    @Test
    fun gracefulDecodeViaRunCatchingReturnsNullForV2() {
        // Симулирует поведение EncryptedLocalSessionStore.current():
        // runCatching { json.decodeFromString<SessionRecord>(blob) }.getOrNull()
        val decoded = runCatching {
            json.decodeFromString<SessionRecord>(SessionRecordFixtures.V2_HYPOTHETICAL)
        }.getOrNull()
        assertNull(decoded)

        // Для контраста: v1 fixture через тот же путь успешно декодится.
        val decodedV1 = runCatching {
            json.decodeFromString<SessionRecord>(SessionRecordFixtures.V1_CANONICAL)
        }.getOrNull()
        assertNotNull(decodedV1)
        assertEquals(WireVersion(1, 0), decodedV1.schemaVersion)
    }
}
