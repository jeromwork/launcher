package com.launcher.app.data.recovery

import family.keys.api.BackupError
import family.keys.api.KdfParams
import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackupBlob
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-format contract tests for [RecoveryBlobJsonCodec] — the adapter-side DTO/codec
 * that owns the recovery-vault JSON after TASK-141 moved the version + @Serializable out
 * of the crypto type [RecoveryKeyBackupBlob].
 *
 * Consolidates the four contract tests that lived in `:core:keys` jvmTest against the
 * removed `RecoveryBlobCodec` (roundtrip, unsupported-schema, backward-compat, provider-
 * agnostic). The frozen v1/v2 fixtures are inlined here as string constants — the same
 * bytes as the old resource files (contracts/recovery-key-backup-v1.md §8, immutable).
 */
class RecoveryBlobJsonCodecTest {

    private fun sampleBlob(seed: Byte = 0x42) = RecoveryKeyBackupBlob(
        stableId = "00000000-0000-4000-8000-000000000001",
        salt = ByteArray(32) { seed },
        kdfParams = KdfParams(),
        ciphertext = ByteArray(48) { (seed + it).toByte() },
        nonce = ByteArray(24) { (seed - it).toByte() },
        createdAt = Instant.parse("2026-06-28T10:00:00Z"),
    )

    private fun decodeSuccess(json: String): RecoveryKeyBackupBlob {
        val result = RecoveryBlobJsonCodec.decode(json)
        assertTrue("decode MUST succeed, was $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }

    private fun decodeError(json: String): BackupError {
        val result = RecoveryBlobJsonCodec.decode(json)
        assertTrue("decode MUST fail, was $result", result is Outcome.Failure)
        return (result as Outcome.Failure).error
    }

    // ── roundtrip ───────────────────────────────────────────────────────────

    @Test
    fun encodeDecodeSurvivesRoundtrip() {
        val original = sampleBlob()
        assertEquals(original, decodeSuccess(RecoveryBlobJsonCodec.encode(original)))
    }

    @Test
    fun roundtripPreservesKdfParamsAndBytes() {
        val original = sampleBlob(0x77)
        val decoded = decodeSuccess(RecoveryBlobJsonCodec.encode(original))
        assertEquals(original.kdfParams, decoded.kdfParams)
        assertTrue(original.salt.contentEquals(decoded.salt))
        assertTrue(original.ciphertext.contentEquals(decoded.ciphertext))
        assertTrue(original.nonce.contentEquals(decoded.nonce))
    }

    @Test
    fun jsonContainsSchemaVersionField() {
        val json = RecoveryBlobJsonCodec.encode(sampleBlob())
        assertTrue(json.contains("\"schemaVersion\""))
        assertTrue(json.contains("\"schemaVersion\":1") || json.contains("\"schemaVersion\": 1"))
    }

    // ── forward-compat / reader gate ────────────────────────────────────────

    @Test
    fun v2FixtureReturnsUnsupportedSchema() {
        val error = decodeError(V2_FUTURE_FIXTURE)
        assertTrue("expected UnsupportedSchema, was $error", error is BackupError.UnsupportedSchema)
        assertEquals(2, (error as BackupError.UnsupportedSchema).version)
    }

    @Test
    fun missingSchemaVersionReturnsMalformed() {
        val json = """{"stableId":"00000000-0000-4000-8000-000000000001","salt":"AAAA","kdfParams":{"algorithm":"Argon2id","iterations":3,"memoryKb":65536,"parallelism":1},"ciphertext":"AAAA","nonce":"AAAA","createdAt":"2026-06-28T10:00:00Z"}"""
        assertTrue(decodeError(json) is BackupError.Malformed)
    }

    @Test
    fun invalidJsonReturnsMalformed() {
        assertTrue(decodeError("not-valid-json") is BackupError.Malformed)
    }

    @Test
    fun nonObjectJsonReturnsMalformed() {
        assertTrue(decodeError("[1, 2, 3]") is BackupError.Malformed)
    }

    // ── backward-compat: frozen v1 fixture ──────────────────────────────────

    @Test
    fun v1FixtureDecodesWithAllFields() {
        val blob = decodeSuccess(V1_FIXTURE)
        assertEquals("Argon2id", blob.kdfParams.algorithm)
        assertTrue(blob.stableId.isNotEmpty())
        assertTrue(blob.salt.isNotEmpty())
        assertTrue(blob.ciphertext.isNotEmpty())
        assertTrue(blob.nonce.isNotEmpty())
        assertTrue(blob.createdAt.toEpochMilliseconds() > 0)
    }

    // ── provider-agnostic (SC-008, contracts §4) ────────────────────────────

    @Test
    fun encodedBlobAndV1FixtureHaveNoForbiddenFields() {
        assertNoForbiddenFields(RecoveryBlobJsonCodec.encode(sampleBlob()))
        assertNoForbiddenFields(V1_FIXTURE)
    }

    private fun assertNoForbiddenFields(jsonString: String) {
        val element = Json.parseToJsonElement(jsonString)
        assertTrue("JSON must be an object", element is JsonObject)
        val violations = element.jsonObject.keys.filter { it in FORBIDDEN_KEYS }
        assertTrue("Forbidden provider fields present: $violations", violations.isEmpty())
    }

    private companion object {
        val FORBIDDEN_KEYS = setOf(
            "googleSub", "googleAccountId", "firebaseUid", "providerKind", "providerId",
            "email", "phoneNumber", "displayName", "recipientId", "groupId",
        )

        // Frozen v1 fixture (contracts §8, immutable) — same bytes as the retired
        // core/keys jvmTest resource recovery-blob-v1-sample.json.
        const val V1_FIXTURE = """{"schemaVersion":1,"stableId":"00000000-0000-4000-8000-000000000001","salt":"Tx5LqK8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cE=","kdfParams":{"algorithm":"Argon2id","iterations":3,"memoryKb":65536,"parallelism":1},"ciphertext":"P3vG2X5n0K8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cD5fHaB+cD/eFgHiJkLmNoPqRsTuVwXyZ0123==","nonce":"B+gWQpA9Fk1tR8nXmYzQwLpEsT2cD5fH","createdAt":"2026-06-28T10:00:00Z"}"""

        const val V2_FUTURE_FIXTURE = """{"schemaVersion":2,"stableId":"00000000-0000-4000-8000-000000000001","salt":"Tx5LqK8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cE=","kdfParams":{"algorithm":"Argon2id","iterations":3,"memoryKb":65536,"parallelism":1},"ciphertext":"P3vG2X5n0K8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cD5fHaB+cD/eFgHiJkLmNoPqRsTuVwXyZ0123==","nonce":"B+gWQpA9Fk1tR8nXmYzQwLpEsT2cD5fH","createdAt":"2026-06-28T10:00:00Z"}"""
    }
}
