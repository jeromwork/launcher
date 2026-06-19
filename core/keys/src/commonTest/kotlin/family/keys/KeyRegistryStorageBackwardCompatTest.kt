package family.keys

import family.keys.api.WrappedDek
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Backward-compat test для wire-format'а DEK persistence (T105, T106, CLAUDE.md rule 5).
 *
 * Хранилище DEK'ов в production использует SecureKeyStore (binary), но wire-format
 * самого [WrappedDek] (когда мы захотим persist его как JSON — например, для backup
 * или migration) должен оставаться backward-compat.
 *
 * Этот test frozen JSON fixture описывающий 3 DEK'а (S-2 pair, S-5 photo, F-5 config)
 * — будущий код MUST уметь decode'нуть это без crash'а.
 */
class KeyRegistryStorageBackwardCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Frozen v1 fixture — 3 DEK'а как WrappedDek (per FR-005). Каждый со своим
     * schemaVersion=1, algorithm="xchacha20poly1305-v1".
     */
    private val v1MultiDekFixture = """
        [
          {
            "name": "config-cipher-aead-v1",
            "schemaVersion": 1,
            "algorithm": "xchacha20poly1305-v1",
            "ciphertext": "AAECAwQFBgcICQ==",
            "nonce": "AAECAwQFBgcICQoLDA0ODxAREhMUFRYX"
          },
          {
            "name": "pair-x25519-v1",
            "schemaVersion": 1,
            "algorithm": "xchacha20poly1305-v1",
            "ciphertext": "EBESExQVFhcYGRobHB0eHw==",
            "nonce": "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3"
          },
          {
            "name": "photo-aead-v1",
            "schemaVersion": 1,
            "algorithm": "xchacha20poly1305-v1",
            "ciphertext": "ODk6Ozw9Pj9AQUJDREVGRw==",
            "nonce": "SElKS0xNTk9QUVJTVFVWV1hZWltcXV5f"
          }
        ]
    """.trimIndent()

    @Test
    fun v1MultiDekFixtureDecodes() {
        val parsed = json.decodeFromString<List<WrappedDek>>(v1MultiDekFixture)
        assertEquals(3, parsed.size)
        assertEquals("config-cipher-aead-v1", parsed[0].name)
        assertEquals("pair-x25519-v1", parsed[1].name)
        assertEquals("photo-aead-v1", parsed[2].name)
        for (w in parsed) {
            assertEquals(1, w.schemaVersion)
            assertEquals("xchacha20poly1305-v1", w.algorithm)
            assertEquals(24, w.nonce.size)
        }
    }

    @Test
    fun unknownDekWithFutureSchemaVersionIgnoredGracefully() {
        // FR-005: storage с DEK от будущего spec'а — не падает. Можно скипнуть unknown.
        val fixture = """
            [
              {
                "name": "config-cipher-aead-v1",
                "schemaVersion": 1,
                "algorithm": "xchacha20poly1305-v1",
                "ciphertext": "AA==",
                "nonce": "AAECAwQFBgcICQoLDA0ODxAREhMUFRYX"
              },
              {
                "name": "future-v99-dek",
                "schemaVersion": 99,
                "algorithm": "kyber-768-experimental",
                "ciphertext": "AA==",
                "nonce": "AAECAwQFBgcICQoLDA0ODxAREhMUFRYX",
                "extraFutureField": "ignored"
              }
            ]
        """.trimIndent()
        val parsed = json.decodeFromString<List<WrappedDek>>(fixture)
        assertEquals(2, parsed.size)
        assertEquals(99, parsed[1].schemaVersion)
        // Каждый DEK parse'ится; runtime код решает обрабатывать unknown algorithm
        // как UnknownDek (см. KeyRegistryImpl.getDek tests).
    }
}
