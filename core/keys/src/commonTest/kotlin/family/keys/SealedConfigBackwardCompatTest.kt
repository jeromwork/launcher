package family.keys

import family.keys.api.SealedConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Backward-compat test для [SealedConfig] wire-format (T053, CLAUDE.md rule 5).
 *
 * Использует frozen JSON fixture — копия того, что v1 client запишет в Firestore.
 * Будущие клиенты MUST уметь decode'нуть этот JSON без crash'а.
 *
 * Polный E2E roundtrip с fixture key + nonce + plaintext равенство — это часть
 * AndroidTest layer (Lane B, T056 Firestore Emulator E2E + fixture-key
 * AdvancedBackwardCompatTest когда понадобится).
 */
class SealedConfigBackwardCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Frozen v1 fixture. Был сгенерирован 2026-06-19 v1 client'ом.
     *
     * При изменении wire-format нужно:
     *  1. Bump SCHEMA_VERSION в SealedConfig.
     *  2. Добавить миграцию для v1 → v2 read.
     *  3. ДОБАВИТЬ новый fixture для v2; этот test остаётся для v1.
     */
    private val v1Fixture = """
        {
          "schemaVersion": 1,
          "algorithm": "xchacha20poly1305-v1",
          "ciphertext": "AAECAwQFBgcICQ==",
          "nonce": "AAECAwQFBgcICQoLDA0ODxAREhMUFRYX",
          "aad": "ZjUtY29uZmlnOjp0ZXN0LXVpZDo6djE=",
          "recipientMasterSignature": null
        }
    """.trimIndent()

    @Test
    fun v1FixtureDecodes() {
        val parsed = json.decodeFromString<SealedConfig>(v1Fixture)
        assertEquals(1, parsed.schemaVersion)
        assertEquals("xchacha20poly1305-v1", parsed.algorithm)
        assertEquals(10, parsed.ciphertext.size)
        assertEquals(24, parsed.nonce.size)
        assertNull(parsed.recipientMasterSignature)
    }

    @Test
    fun v1FixtureRoundtripStable() {
        val parsed = json.decodeFromString<SealedConfig>(v1Fixture)
        val text = Json { encodeDefaults = true; prettyPrint = false }.encodeToString(SealedConfig.serializer(), parsed)
        // Re-parse — equivalence check (string compare ненадёжен из-за field ordering).
        val reparsed = json.decodeFromString<SealedConfig>(text)
        assertEquals(parsed, reparsed)
    }

    @Test
    fun futureFieldsIgnored() {
        val fwd = """
            {
              "schemaVersion": 1,
              "algorithm": "xchacha20poly1305-v1",
              "ciphertext": "AA==",
              "nonce": "AAECAwQFBgcICQoLDA0ODxAREhMUFRYX",
              "aad": "AA==",
              "newFutureField": "future-value-here",
              "anotherFutureField": 42
            }
        """.trimIndent()
        val parsed = json.decodeFromString<SealedConfig>(fwd)
        assertEquals(1, parsed.schemaVersion)
    }
}
