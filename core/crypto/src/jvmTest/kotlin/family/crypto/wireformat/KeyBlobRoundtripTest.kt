package family.crypto.wireformat

import family.crypto.api.values.KeyBlob
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyBlobRoundtripTest {

    private val json = Json { prettyPrint = false }

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
        // Must NOT contain any base64 of the wrapped key payload.
        assertEquals(false, s.contains("AQID"), "toString leaked base64 of wrappedKey")
    }
}
