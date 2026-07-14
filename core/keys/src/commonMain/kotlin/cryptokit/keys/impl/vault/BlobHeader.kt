package cryptokit.keys.impl.vault

import cryptokit.keys.api.vault.VaultException

/**
 * `magic(2) || format_version(1) || purpose_id(2 BE) || key_epoch(2 BE) || nonce(24) || aead_payload_with_mac`
 * per FR-003.
 *
 * The header is authenticated as part of the AEAD associated data by the vault adapter — a
 * caller cannot rewrite `purpose_id` without invalidating the MAC.
 */
internal object BlobHeader {
    const val MAGIC_0: Byte = 0x4B
    const val MAGIC_1: Byte = 0x56
    const val FORMAT_VERSION: Byte = 0x01
    const val NONCE_SIZE: Int = 24

    /** offset where the AEAD payload starts (magic 2 + fmt 1 + purposeId 2 + keyEpoch 2 + nonce 24). */
    const val HEADER_SIZE: Int = 2 + 1 + 2 + 2 + NONCE_SIZE

    fun pack(
        purposeStableId: Int,
        keyEpoch: Int,
        nonce: ByteArray,
        aeadPayload: ByteArray,
    ): ByteArray {
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes, got ${nonce.size}" }
        require(purposeStableId in 0..0xFFFF) { "purposeStableId out of range: $purposeStableId" }
        require(keyEpoch in 0..0xFFFF) { "keyEpoch out of range: $keyEpoch" }

        val out = ByteArray(HEADER_SIZE + aeadPayload.size)
        var p = 0
        out[p++] = MAGIC_0
        out[p++] = MAGIC_1
        out[p++] = FORMAT_VERSION
        out[p++] = ((purposeStableId ushr 8) and 0xFF).toByte()
        out[p++] = (purposeStableId and 0xFF).toByte()
        out[p++] = ((keyEpoch ushr 8) and 0xFF).toByte()
        out[p++] = (keyEpoch and 0xFF).toByte()
        nonce.copyInto(out, p); p += NONCE_SIZE
        aeadPayload.copyInto(out, p)
        return out
    }

    /**
     * Parse a header. Throws on structural problems; the AEAD MAC is verified separately by
     * the caller (payload bytes are returned unchecked here).
     */
    fun parse(bytes: ByteArray): Parsed {
        if (bytes.size < HEADER_SIZE) {
            throw VaultException.TamperDetected("Blob too short: ${bytes.size} < $HEADER_SIZE")
        }
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) {
            throw VaultException.TamperDetected("Bad magic bytes")
        }
        val version = bytes[2].toInt() and 0xFF
        if (version != (FORMAT_VERSION.toInt() and 0xFF)) {
            throw VaultException.UnsupportedFormatVersion(version)
        }
        val purposeId = ((bytes[3].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)
        val keyEpoch = ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[6].toInt() and 0xFF)
        val nonce = bytes.copyOfRange(7, 7 + NONCE_SIZE)
        val payload = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        return Parsed(version, purposeId, keyEpoch, nonce, payload)
    }

    data class Parsed(
        val formatVersion: Int,
        val purposeStableId: Int,
        val keyEpoch: Int,
        val nonce: ByteArray,
        val aeadPayload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean = other is Parsed &&
            formatVersion == other.formatVersion &&
            purposeStableId == other.purposeStableId &&
            keyEpoch == other.keyEpoch &&
            nonce.contentEquals(other.nonce) &&
            aeadPayload.contentEquals(other.aeadPayload)

        override fun hashCode(): Int {
            var h = formatVersion
            h = 31 * h + purposeStableId
            h = 31 * h + keyEpoch
            h = 31 * h + nonce.contentHashCode()
            h = 31 * h + aeadPayload.contentHashCode()
            return h
        }
    }
}
