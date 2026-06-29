package family.keys.contracts

import family.keys.impl.RecoveryBlobCodec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.api.KdfParams
import kotlinx.datetime.Instant

/**
 * Contract test: RecoveryKeyBackupBlob provider-agnostic JSON schema (T624, SC-008, FR-023).
 *
 * Парсит JSON top-level key set и assertирует ОТСУТСТВИЕ запрещённых полей (contracts §4).
 * Это fitness function — проверяет что blob не содержит identity-provider leakage.
 *
 * Note (B3): Fixture loading is placed in `jvmTest` instead of `commonTest`
 * because resource loading utilizes `this::class.java.classLoader.getResourceAsStream(...)`
 * which is JVM-specific.
 */
class RecoveryKeyBackupBlobProviderAgnosticTest {

    /** Запрещённые top-level JSON keys (contracts/recovery-key-backup-v1.md §4). */
    private val forbiddenKeys = setOf(
        "googleSub",
        "googleAccountId",
        "firebaseUid",
        "providerKind",
        "providerId",
        "email",
        "phoneNumber",
        "displayName",
        "recipientId",
        "groupId",
    )

    @Test
    fun encodedBlobHasNoForbiddenFields() {
        val blob = RecoveryKeyBackupBlob(
            stableId = "00000000-0000-4000-8000-000000000001",
            salt = ByteArray(32) { 0x42 },
            kdfParams = KdfParams(),
            ciphertext = ByteArray(48) { it.toByte() },
            nonce = ByteArray(24) { it.toByte() },
            createdAt = Instant.parse("2026-06-28T10:00:00Z")
        )
        val json = RecoveryBlobCodec.encode(blob)
        assertNoForbiddenFields(json)
    }

    @Test
    fun v1FixtureHasNoForbiddenFields() {
        val stream = this::class.java.classLoader
            ?.getResourceAsStream("fixtures/recovery-blob-v1-sample.json")
            ?: error("Fixture not found: fixtures/recovery-blob-v1-sample.json")
        val json = stream.bufferedReader().readText()
        assertNoForbiddenFields(json)
    }

    private fun assertNoForbiddenFields(jsonString: String) {
        val jsonElement = Json.parseToJsonElement(jsonString)
        assertIs<JsonObject>(jsonElement, "JSON must be an object")

        val topLevelKeys = jsonElement.jsonObject.keys
        val violations = topLevelKeys.filter { it in forbiddenKeys }

        assertFalse(
            violations.isNotEmpty(),
            "RecoveryKeyBackupBlob MUST NOT contain provider-specific fields (SC-008, contracts §4). " +
                "Found forbidden keys: $violations"
        )
    }
}
