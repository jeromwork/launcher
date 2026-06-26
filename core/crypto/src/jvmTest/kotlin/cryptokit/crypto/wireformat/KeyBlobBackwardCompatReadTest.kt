package cryptokit.crypto.wireformat

import cryptokit.crypto.api.values.KeyBlob
import cryptokit.crypto.exception.CryptoException
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KeyBlobBackwardCompatReadTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun v1SampleFixtureReadsSuccessfully() {
        val text = readResource("/key-blob/v1-sample.json")
        val blob = json.decodeFromString(KeyBlob.serializer(), text)
        assertEquals(1, blob.schemaVersion)
        assertEquals("X25519", blob.algorithm)
        assertEquals(Instant.parse("2026-06-17T10:30:00Z"), blob.createdAt)
        assertNull(blob.retiredAt)
        assertNull(blob.replacedBy)
        assertEquals("family-crypto-wrap-key-v1", blob.wrapKeyAlias)
    }

    @Test
    fun v1RetiredSampleFixtureReadsSuccessfully() {
        val text = readResource("/key-blob/v1-retired-sample.json")
        val blob = json.decodeFromString(KeyBlob.serializer(), text)
        assertEquals(1, blob.schemaVersion)
        assertEquals(Instant.parse("2026-06-17T10:30:00Z"), blob.retiredAt)
        assertEquals("config-admin-identity-v2", blob.replacedBy)
    }

    @Test
    fun unknownFutureSchemaVersion_throwsUnsupportedSchemaVersion() {
        val futureBlob = """
            {
              "schemaVersion": 999,
              "algorithm": "X25519",
              "createdAt": "2026-06-17T10:30:00Z",
              "retiredAt": null,
              "replacedBy": null,
              "wrappedKey": "AAAA",
              "iv": "AAAAAAAAAAAAAAAA",
              "wrapKeyAlias": "family-crypto-wrap-key-v1"
            }
        """.trimIndent()
        assertFailsWith<CryptoException.UnsupportedSchemaVersion> {
            readKeyBlobWithSchemaCheck(futureBlob)
        }
    }

    private fun readKeyBlobWithSchemaCheck(text: String): KeyBlob {
        // Peek at schemaVersion before fully decoding, mirroring the production read path
        // described in contracts/key-blob-v1.md §"Read" step 6.
        val tree = json.parseToJsonElement(text) as JsonObject
        val version = (tree["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: throw CryptoException.KeyBlobDeserializationFailed("missing schemaVersion")
        if (version > KeyBlob.CURRENT_SCHEMA_VERSION) {
            throw CryptoException.UnsupportedSchemaVersion(
                found = version,
                known = KeyBlob.CURRENT_SCHEMA_VERSION
            )
        }
        return json.decodeFromString(KeyBlob.serializer(), text)
    }

    private fun readResource(path: String): String {
        val stream = this::class.java.getResourceAsStream(path)
            ?: error("Resource not found on classpath: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
