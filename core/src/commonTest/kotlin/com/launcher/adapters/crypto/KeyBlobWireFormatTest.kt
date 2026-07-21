package com.launcher.adapters.crypto

import family.crypto.exception.CryptoException
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Wire-format tests for [KeyBlob] — the on-disk persistence format that TASK-141 moved out
 * of `:core:crypto` into the adapter layer (`FileKeyBlobStore`). Replaces the crypto-module
 * KeyBlobRoundtripTest + KeyBlobBackwardCompatReadTest; the frozen v1 fixtures are inlined
 * here verbatim (same bytes as the retired core/crypto resource files).
 */
class KeyBlobWireFormatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun keyBlobRoundtripsToIdenticalContent() {
        val original = KeyBlob(
            schemaVersion = 1,
            algorithm = "X25519",
            createdAt = Instant.parse("2026-06-17T10:30:00Z"),
            retiredAt = null,
            replacedBy = null,
            wrappedKey = byteArrayOf(1, 2, 3, 4, 5),
            iv = ByteArray(12) { it.toByte() },
            wrapKeyAlias = "family-crypto-wrap-key-v1"
        )
        val encoded = json.encodeToString(KeyBlob.serializer(), original)
        val parsed = json.decodeFromString(KeyBlob.serializer(), encoded)

        assertEquals(original.schemaVersion, parsed.schemaVersion)
        assertEquals(original.algorithm, parsed.algorithm)
        assertEquals(original.createdAt, parsed.createdAt)
        assertNull(parsed.retiredAt)
        assertNull(parsed.replacedBy)
        assertContentEquals(original.wrappedKey, parsed.wrappedKey)
        assertContentEquals(original.iv, parsed.iv)
        assertEquals(original.wrapKeyAlias, parsed.wrapKeyAlias)
        assertEquals(original, parsed)
    }

    @Test
    fun keyBlobToStringRedactsRawBytes() {
        val blob = KeyBlob(
            algorithm = "X25519",
            createdAt = Instant.parse("2026-06-17T10:30:00Z"),
            wrappedKey = byteArrayOf(0x01, 0x02, 0x03),
            iv = ByteArray(12),
            wrapKeyAlias = "family-crypto-wrap-key-v1"
        )
        val s = blob.toString()
        assertEquals(true, s.contains("wrappedKey=<3 bytes>"))
        assertEquals(true, s.contains("iv=<12 bytes>"))
        assertEquals(false, s.contains("AQID"), "toString leaked base64 of wrappedKey")
    }

    @Test
    fun v1FixtureReadsSuccessfully() {
        val blob = json.decodeFromString(KeyBlob.serializer(), V1_SAMPLE)
        assertEquals(1, blob.schemaVersion)
        assertEquals("X25519", blob.algorithm)
        assertEquals(Instant.parse("2026-06-17T10:30:00Z"), blob.createdAt)
        assertNull(blob.retiredAt)
        assertNull(blob.replacedBy)
        assertEquals("family-crypto-wrap-key-v1", blob.wrapKeyAlias)
    }

    @Test
    fun v1RetiredFixtureReadsSuccessfully() {
        val blob = json.decodeFromString(KeyBlob.serializer(), V1_RETIRED_SAMPLE)
        assertEquals(1, blob.schemaVersion)
        assertEquals(Instant.parse("2026-06-17T10:30:00Z"), blob.retiredAt)
        assertEquals("config-admin-identity-v2", blob.replacedBy)
    }

    @Test
    fun unknownFutureSchemaVersionThrowsUnsupportedSchemaVersion() {
        val futureBlob = """{"schemaVersion":999,"algorithm":"X25519","createdAt":"2026-06-17T10:30:00Z","retiredAt":null,"replacedBy":null,"wrappedKey":"AAAA","iv":"AAAAAAAAAAAAAAAA","wrapKeyAlias":"family-crypto-wrap-key-v1"}"""
        assertFailsWith<CryptoException.UnsupportedSchemaVersion> {
            readKeyBlobWithSchemaCheck(futureBlob)
        }
    }

    // Mirrors the production read path (FileKeyBlobStore.read): peek schemaVersion, gate,
    // then decode.
    private fun readKeyBlobWithSchemaCheck(text: String): KeyBlob {
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

    private companion object {
        const val V1_SAMPLE = """{"schemaVersion":1,"algorithm":"X25519","createdAt":"2026-06-17T10:30:00Z","retiredAt":null,"replacedBy":null,"wrappedKey":"ZGV0ZXJtaW5pc3RpYy10ZXN0LXdyYXBwZWQta2V5LWJ5dGVzAAAAAAAAAAA=","iv":"AAAAAAAAAAAAAAAA","wrapKeyAlias":"family-crypto-wrap-key-v1"}"""

        const val V1_RETIRED_SAMPLE = """{"schemaVersion":1,"algorithm":"X25519","createdAt":"2026-01-01T00:00:00Z","retiredAt":"2026-06-17T10:30:00Z","replacedBy":"config-admin-identity-v2","wrappedKey":"ZGV0ZXJtaW5pc3RpYy10ZXN0LXdyYXBwZWQta2V5LWJ5dGVzAAAAAAAAAAA=","iv":"AAAAAAAAAAAAAAAA","wrapKeyAlias":"family-crypto-wrap-key-v1"}"""
    }
}
