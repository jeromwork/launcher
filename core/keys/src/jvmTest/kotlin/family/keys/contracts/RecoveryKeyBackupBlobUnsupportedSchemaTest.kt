package family.keys.contracts

import family.keys.api.BackupError
import family.keys.api.Outcome
import family.keys.impl.RecoveryBlobCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Contract test: UnsupportedSchema forward-compat (T625, contracts/recovery-key-backup-v1.md §5).
 *
 * Fixture с schemaVersion=2 → decode MUST return BackupError.UnsupportedSchema(version=2).
 * Никакого partial-parse leakage — caller не получает никаких полей.
 *
 * Note (B3): Fixture loading is placed in `jvmTest` instead of `commonTest`
 * because resource loading utilizes `this::class.java.classLoader.getResourceAsStream(...)`
 * which is JVM-specific.
 */
class RecoveryKeyBackupBlobUnsupportedSchemaTest {

    @Test
    fun schemaVersion2ReturnsUnsupportedSchema() {
        val json = loadFixture("recovery-blob-v2-sample-future.json")
        val result = RecoveryBlobCodec.decode(json)

        assertIs<Outcome.Failure<BackupError>>(result, "v2 blob MUST return Failure")
        val error = result.error
        assertIs<BackupError.UnsupportedSchema>(error, "Error MUST be UnsupportedSchema")
        assertEquals(2, error.version, "UnsupportedSchema.version MUST be 2")
    }

    @Test
    fun missingSchemaVersionReturnsMalformed() {
        val json = """{"algorithm":"argon2id-xchacha20poly1305-v1","kdfSalt":"AAAA","wrappedRootKey":"AAAA","nonce":"AAAA","createdAt":0}"""
        val result = RecoveryBlobCodec.decode(json)
        assertIs<Outcome.Failure<BackupError>>(result)
        assertIs<BackupError.Malformed>(result.error, "Missing schemaVersion MUST return Malformed")
    }

    @Test
    fun invalidJsonReturnsMalformed() {
        val result = RecoveryBlobCodec.decode("not-valid-json")
        assertIs<Outcome.Failure<BackupError>>(result)
        assertIs<BackupError.Malformed>(result.error)
    }

    @Test
    fun nonObjectJsonReturnsMalformed() {
        val result = RecoveryBlobCodec.decode("[1, 2, 3]")
        assertIs<Outcome.Failure<BackupError>>(result)
        assertIs<BackupError.Malformed>(result.error)
    }

    private fun loadFixture(filename: String): String {
        val stream = this::class.java.classLoader
            ?.getResourceAsStream("fixtures/$filename")
            ?: error("Fixture not found: fixtures/$filename")
        return stream.bufferedReader().readText()
    }
}
