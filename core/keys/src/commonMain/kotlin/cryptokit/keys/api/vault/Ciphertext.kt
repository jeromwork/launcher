package cryptokit.keys.api.vault

/**
 * Opaque encrypted blob returned by [KeyVault.aeadSeal].
 *
 * Wire layout per FR-003 (see [BlobHeader] for the packer):
 *
 * ```
 * magic(2)=0x4B 0x56 || format_version(1)=0x01 || purpose_id(2 BE) || key_epoch(2 BE)
 *   || nonce(24) || aead_payload_with_mac
 * ```
 *
 * The accessors below parse the header on demand — they don't cache, and they don't validate
 * the payload. Full validation (magic + version + purpose match + AEAD MAC) happens inside
 * [KeyVault.aeadOpen].
 */
class Ciphertext(val bytes: ByteArray) {

    /** Format version byte at offset 2. See FR-003. */
    val formatVersion: Int get() = bytes[2].toInt() and 0xFF

    /** Purpose stable-id (2 BE) at offset 3. See [Purpose.stableId]. */
    val purposeId: Int get() =
        ((bytes[3].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)

    /** Key epoch (2 BE) at offset 5. Currently always `0` (rotation deferred). */
    val keyEpoch: Int get() =
        ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[6].toInt() and 0xFF)

    override fun equals(other: Any?): Boolean =
        other is Ciphertext && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "Ciphertext(${bytes.size} bytes)"
}
