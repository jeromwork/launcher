package family.keys.contracts

import family.keys.api.BackupError
import family.keys.api.Outcome
import family.keys.impl.RecoveryBlobCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Contract test: RecoveryKeyBackupBlob backward compatibility (T623, SC-013, FR-023).
 *
 * Читает зафиксированный fixture `recovery-blob-v1-sample.json` (immutable per contract §8)
 * и проверяет что все v1 поля правильно десериализуются.
 *
 * Этот тест НИКОГДА не должен падать от рефакторинга serialization кода.
 * Если он падает — breaking change в v1 reader.
 *
 * Note (B3): Fixture loading is placed in `jvmTest` instead of `commonTest`
 * because resource loading utilizes `this::class.java.classLoader.getResourceAsStream(...)`
 * which is JVM-specific.
 */
class RecoveryKeyBackupBlobContractBackwardCompatTest {

    @Test
    fun v1FixtureDecodesSuccessfully() {
        val json = loadFixture("recovery-blob-v1-sample.json")
        val result = RecoveryBlobCodec.decode(json)
        assertIs<Outcome.Success<*>>(result, "v1 fixture MUST decode successfully")
    }

    @Test
    fun v1FixtureHasCorrectSchemaVersion() {
        val json = loadFixture("recovery-blob-v1-sample.json")
        val blob = (RecoveryBlobCodec.decode(json) as Outcome.Success).value
        assertEquals(1, blob.schemaVersion, "v1 fixture schemaVersion MUST be 1")
    }

    @Test
    fun v1FixtureHasAllRequiredFields() {
        val json = loadFixture("recovery-blob-v1-sample.json")
        val blob = (RecoveryBlobCodec.decode(json) as Outcome.Success).value

        // All required fields must be non-null and non-empty.
        assertTrue(blob.stableId.isNotEmpty(), "stableId MUST be present")
        assertTrue(blob.salt.isNotEmpty(), "salt MUST be present")
        assertTrue(blob.ciphertext.isNotEmpty(), "ciphertext MUST be present")
        assertTrue(blob.nonce.isNotEmpty(), "nonce MUST be present")
        assertTrue(blob.createdAt.toEpochMilliseconds() > 0, "createdAt MUST be valid")
    }

    @Test
    fun v1FixtureAlgorithmMatches() {
        val json = loadFixture("recovery-blob-v1-sample.json")
        val blob = (RecoveryBlobCodec.decode(json) as Outcome.Success).value
        assertEquals(
            "Argon2id",
            blob.kdfParams.algorithm,
            "v1 fixture kdfParams algorithm MUST be 'Argon2id'"
        )
    }

    @Test
    fun v2FixtureReturnsUnsupportedSchema() {
        val json = loadFixture("recovery-blob-v2-sample-future.json")
        val result = RecoveryBlobCodec.decode(json)
        assertIs<Outcome.Failure<BackupError>>(result, "v2 fixture MUST return Failure")
        val error = result.error
        assertIs<BackupError.UnsupportedSchema>(error, "v2 fixture error MUST be UnsupportedSchema")
        assertEquals(2, error.version, "UnsupportedSchema.version MUST be 2")
    }

    // --- helpers ---

    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) error(message)
    }

    private fun loadFixture(filename: String): String {
        val stream = this::class.java.classLoader
            ?.getResourceAsStream("fixtures/$filename")
            ?: error("Fixture not found: fixtures/$filename")
        return stream.bufferedReader().readText()
    }
}
