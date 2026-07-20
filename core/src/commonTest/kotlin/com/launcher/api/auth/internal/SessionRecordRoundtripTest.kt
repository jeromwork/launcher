package com.launcher.api.auth.internal

import com.launcher.wire.WireVersion

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Roundtrip-тест wire format [SessionRecord] v1: encode → decode = identity.
 *
 * Per spec 017 FR-022, SC-009, contract `session-record-v1.md`,
 * CLAUDE.md §5 (wire-format roundtrip requirement).
 */
class SessionRecordRoundtripTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun encodeDecodeIdentity() {
        val original = SessionRecord(
            schemaVersion = WireVersion(1, 0),
            stableId = "550e8400-e29b-41d4-a716-446655440000",
            expiresAtEpochMillis = 1739456789000L,
            refreshToken = "1//04test-refresh-token-stable-fixture",
            extra = mapOf("firebase_jwt" to "eyJhbGciOiJSUzI1NiIs.test.payload"),
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SessionRecord>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun encodeDecodeIdentityWithNullables() {
        // expiresAtEpochMillis + refreshToken nullable — local-only session
        // (анонимная или с протухшим refresh token, до signOut'а).
        val original = SessionRecord(
            schemaVersion = WireVersion(1, 0),
            stableId = "00000000-0000-0000-0000-000000000000",
            expiresAtEpochMillis = null,
            refreshToken = null,
            extra = emptyMap(),
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SessionRecord>(encoded)

        assertEquals(original, decoded)
    }
}
