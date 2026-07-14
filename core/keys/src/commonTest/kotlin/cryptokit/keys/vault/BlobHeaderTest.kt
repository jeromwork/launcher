package cryptokit.keys.vault

import cryptokit.keys.api.vault.VaultException
import cryptokit.keys.impl.vault.BlobHeader
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlobHeaderTest {

    private val nonce = ByteArray(BlobHeader.NONCE_SIZE) { it.toByte() }
    private val payload = byteArrayOf(0x10, 0x20, 0x30, 0x40)

    @Test
    fun `pack then parse roundtrips`() {
        val packed = BlobHeader.pack(purposeStableId = 0x0001, keyEpoch = 0, nonce = nonce, aeadPayload = payload)
        val parsed = BlobHeader.parse(packed)
        assertEquals(0x01, parsed.formatVersion)
        assertEquals(0x0001, parsed.purposeStableId)
        assertEquals(0, parsed.keyEpoch)
        assertContentEquals(nonce, parsed.nonce)
        assertContentEquals(payload, parsed.aeadPayload)
    }

    @Test
    fun `magic bytes at offsets 0 and 1`() {
        val packed = BlobHeader.pack(0x0001, 0, nonce, payload)
        assertEquals(BlobHeader.MAGIC_0, packed[0])
        assertEquals(BlobHeader.MAGIC_1, packed[1])
    }

    @Test
    fun `bad magic rejected`() {
        val packed = BlobHeader.pack(0x0001, 0, nonce, payload)
        packed[0] = 0x00
        assertFailsWith<VaultException.TamperDetected> { BlobHeader.parse(packed) }
    }

    @Test
    fun `unsupported format version rejected`() {
        val packed = BlobHeader.pack(0x0001, 0, nonce, payload)
        packed[2] = 0x99.toByte()
        assertFailsWith<VaultException.UnsupportedFormatVersion> { BlobHeader.parse(packed) }
    }

    @Test
    fun `truncated blob rejected`() {
        val short = byteArrayOf(BlobHeader.MAGIC_0, BlobHeader.MAGIC_1)
        assertFailsWith<VaultException.TamperDetected> { BlobHeader.parse(short) }
    }

    @Test
    fun `purpose_id and key_epoch big-endian`() {
        val packed = BlobHeader.pack(purposeStableId = 0x1234, keyEpoch = 0x00AB, nonce = nonce, aeadPayload = payload)
        assertEquals(0x12.toByte(), packed[3])
        assertEquals(0x34.toByte(), packed[4])
        assertEquals(0x00.toByte(), packed[5])
        assertEquals(0xAB.toByte(), packed[6])
    }
}
