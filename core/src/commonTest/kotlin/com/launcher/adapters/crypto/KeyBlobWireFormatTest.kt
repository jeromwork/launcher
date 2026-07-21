package com.launcher.adapters.crypto

import family.crypto.exception.CryptoException
import family.wire.WireVersion
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Wire-format tests for [KeyBlob] — the on-disk persistence format that TASK-141 moved out
 * of `:core:crypto` into the adapter layer (`FileKeyBlobStore`). Replaces the crypto-module
 * KeyBlobRoundtripTest + KeyBlobBackwardCompatReadTest.
 *
 * Part D converts the version from the bare integer (`"schemaVersion":1`) to the dotted
 * three-field header (`"schemaVersion":"1.0"` + `minReaderVersion` + `minWriterVersion`). The
 * pre-conversion integer form is deliberately NOT kept as a backward-compat fixture: no user
 * data exists (pre-MVP), and an int document now fails to parse — the reader is refused rather
 * than guessing, which is the intended lockout the migration accepts.
 */
class KeyBlobWireFormatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun keyBlobRoundtripsToIdenticalContent() {
        val original = KeyBlob(
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

        assertEquals(WireVersion(1, 0), parsed.schemaVersion)
        assertEquals(WireVersion(1, 0), parsed.minReaderVersion)
        assertEquals(WireVersion(1, 0), parsed.minWriterVersion)
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
    fun versionHeaderEncodesAsDottedStrings() {
        val encoded = json.encodeToString(
            KeyBlob.serializer(),
            KeyBlob(
                algorithm = "X25519",
                createdAt = Instant.parse("2026-06-17T10:30:00Z"),
                wrappedKey = byteArrayOf(1, 2, 3),
                iv = ByteArray(12),
                wrapKeyAlias = "family-crypto-wrap-key-v1"
            )
        )
        assertEquals(true, encoded.contains(""""schemaVersion":"1.0""""))
        assertEquals(true, encoded.contains(""""minReaderVersion":"1.0""""))
        assertEquals(true, encoded.contains(""""minWriterVersion":"1.0""""))
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
        assertEquals(WireVersion(1, 0), blob.schemaVersion)
        assertEquals("X25519", blob.algorithm)
        assertEquals(Instant.parse("2026-06-17T10:30:00Z"), blob.createdAt)
        assertNull(blob.retiredAt)
        assertNull(blob.replacedBy)
        assertEquals("family-crypto-wrap-key-v1", blob.wrapKeyAlias)
    }

    @Test
    fun v1RetiredFixtureReadsSuccessfully() {
        val blob = json.decodeFromString(KeyBlob.serializer(), V1_RETIRED_SAMPLE)
        assertEquals(WireVersion(1, 0), blob.schemaVersion)
        assertEquals(Instant.parse("2026-06-17T10:30:00Z"), blob.retiredAt)
        assertEquals("config-admin-identity-v2", blob.replacedBy)
    }

    @Test
    fun unknownFutureSchemaVersionThrowsUnsupportedSchemaVersion() {
        assertFailsWith<CryptoException.UnsupportedSchemaVersion> {
            readKeyBlobWithSchemaCheck(FUTURE_SAMPLE)
        }
    }

    // Mirrors the production read path (FileKeyBlobStore.read): decode, then gate on the header.
    private fun readKeyBlobWithSchemaCheck(text: String): KeyBlob {
        val blob = json.decodeFromString(KeyBlob.serializer(), text)
        if (blob.minReaderVersion > KeyBlob.SCHEMA_VERSION) {
            throw CryptoException.UnsupportedSchemaVersion(
                found = blob.schemaVersion.major,
                known = KeyBlob.SCHEMA_VERSION.major,
            )
        }
        return blob
    }

    private companion object {
        const val V1_SAMPLE = """{"schemaVersion":"1.0","minReaderVersion":"1.0","minWriterVersion":"1.0","algorithm":"X25519","createdAt":"2026-06-17T10:30:00Z","retiredAt":null,"replacedBy":null,"wrappedKey":"ZGV0ZXJtaW5pc3RpYy10ZXN0LXdyYXBwZWQta2V5LWJ5dGVzAAAAAAAAAAA=","iv":"AAAAAAAAAAAAAAAA","wrapKeyAlias":"family-crypto-wrap-key-v1"}"""

        const val V1_RETIRED_SAMPLE = """{"schemaVersion":"1.0","minReaderVersion":"1.0","minWriterVersion":"1.0","algorithm":"X25519","createdAt":"2026-01-01T00:00:00Z","retiredAt":"2026-06-17T10:30:00Z","replacedBy":"config-admin-identity-v2","wrappedKey":"ZGV0ZXJtaW5pc3RpYy10ZXN0LXdyYXBwZWQta2V5LWJ5dGVzAAAAAAAAAAA=","iv":"AAAAAAAAAAAAAAAA","wrapKeyAlias":"family-crypto-wrap-key-v1"}"""

        // Header demands a reader at 999.0 — above this build's SCHEMA_VERSION, so the gate refuses it.
        const val FUTURE_SAMPLE = """{"schemaVersion":"999.0","minReaderVersion":"999.0","minWriterVersion":"999.0","algorithm":"X25519","createdAt":"2026-06-17T10:30:00Z","retiredAt":null,"replacedBy":null,"wrappedKey":"AAAA","iv":"AAAAAAAAAAAAAAAA","wrapKeyAlias":"family-crypto-wrap-key-v1"}"""
    }
}
