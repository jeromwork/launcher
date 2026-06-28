package family.keys

import family.keys.api.RecoveryKeyBackupBlob
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Backward-compat test для [RecoveryKeyBackupBlob] wire-format (T081, CLAUDE.md rule 5).
 */
class RecoveryKeyBackupBlobBackwardCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Frozen v1 fixture. Сгенерирован 2026-06-19 v1 client'ом.
     */
    private val v1Fixture = """
        {
          "schemaVersion": 1,
          "algorithm": "argon2id-xchacha20poly1305-v1",
          "kdfSalt": "AAECAwQFBgcICQoLDA0ODw==",
          "kdfParams": {
            "memoryKib": 65536,
            "iterations": 3,
            "parallelism": 1
          },
          "wrappedRootKey": "EBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8=",
          "nonce": "MDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZH",
          "createdAt": 1719792000000
        }
    """.trimIndent()

    @Test
    fun v1FixtureDecodes() {
        val parsed = json.decodeFromString<RecoveryKeyBackupBlob>(v1Fixture)
        assertEquals(1, parsed.schemaVersion)
        assertEquals("argon2id-xchacha20poly1305-v1", parsed.algorithm)
        assertEquals(16, parsed.kdfSalt.size)
        assertEquals(65_536, parsed.kdfParams.memoryKib)
        assertEquals(3, parsed.kdfParams.iterations)
        assertEquals(1, parsed.kdfParams.parallelism)
        assertEquals(32, parsed.wrappedRootKey.size)
        assertEquals(24, parsed.nonce.size)
        assertEquals(1_719_792_000_000L, parsed.createdAt)
    }

    @Test
    fun v1FixtureRoundtripStable() {
        val parsed = json.decodeFromString<RecoveryKeyBackupBlob>(v1Fixture)
        val text = Json { encodeDefaults = true }.encodeToString(RecoveryKeyBackupBlob.serializer(), parsed)
        val reparsed = json.decodeFromString<RecoveryKeyBackupBlob>(text)
        assertEquals(parsed, reparsed)
    }

    @Test
    fun futureFieldsIgnored() {
        val fwd = """
            {
              "schemaVersion": 1,
              "algorithm": "argon2id-xchacha20poly1305-v1",
              "kdfSalt": "AAECAwQFBgcICQoLDA0ODw==",
              "kdfParams": { "memoryKib": 65536, "iterations": 3, "parallelism": 1 },
              "wrappedRootKey": "EBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8=",
              "nonce": "MDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZH",
              "createdAt": 1719792000000,
              "futureField1": "value",
              "futureField2": 42
            }
        """.trimIndent()
        val parsed = json.decodeFromString<RecoveryKeyBackupBlob>(fwd)
        assertEquals(1, parsed.schemaVersion)
    }
}
