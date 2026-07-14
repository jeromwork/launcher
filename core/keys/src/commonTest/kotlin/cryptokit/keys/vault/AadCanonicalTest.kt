package cryptokit.keys.vault

import cryptokit.keys.api.vault.canonicalAad
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class AadCanonicalTest {

    @Test
    fun `canonical aad has length-prefixed layout`() {
        val aad = canonicalAad(namespaceId = "ns1", schemaVersion = 1, blobVersion = 7)
        // layout: nsLen(2)=0x00 0x03 || "ns1" || schemaVer(2)=0x00 0x01 || blobVer(2)=0x00 0x07
        val expected = byteArrayOf(
            0x00, 0x03,
            'n'.code.toByte(), 's'.code.toByte(), '1'.code.toByte(),
            0x00, 0x01,
            0x00, 0x07,
        )
        assertContentEquals(expected, aad.bytes)
    }

    @Test
    fun `empty namespace still encodes zero length prefix`() {
        val aad = canonicalAad(namespaceId = "", schemaVersion = 0, blobVersion = 0)
        val expected = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertContentEquals(expected, aad.bytes)
    }

    @Test
    fun `two-byte big-endian encoding for large values`() {
        val aad = canonicalAad(namespaceId = "x", schemaVersion = 0x1234, blobVersion = 0xFFFE)
        // nsLen=1 || "x" || 0x12 0x34 || 0xFF 0xFE
        val expected = byteArrayOf(
            0x00, 0x01,
            'x'.code.toByte(),
            0x12, 0x34,
            0xFF.toByte(), 0xFE.toByte(),
        )
        assertContentEquals(expected, aad.bytes)
    }

    @Test
    fun `rejects out-of-range values`() {
        assertFailsWith<IllegalArgumentException> {
            canonicalAad(namespaceId = "x", schemaVersion = -1, blobVersion = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            canonicalAad(namespaceId = "x", schemaVersion = 0, blobVersion = 0x10000)
        }
    }
}
