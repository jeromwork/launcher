package family.pairing.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("EncryptedEnvelope")
class EncryptedEnvelope(
    val schemaVersion: Int,
    val cipherSuiteId: String,
    val nonce: ByteArray,
    val recipients: List<Recipient>,
    val ciphertext: ByteArray,
    val mac: ByteArray,
    val metadata: Map<String, ByteArray> = emptyMap(),
) {
    init {
        require(recipients.isNotEmpty()) { "envelope must have at least 1 recipient" }
        require(nonce.size == XCHACHA20_NONCE_SIZE) {
            "XChaCha20 nonce must be $XCHACHA20_NONCE_SIZE bytes, got ${nonce.size}"
        }
        require(mac.size == POLY1305_MAC_SIZE) {
            "Poly1305 MAC must be $POLY1305_MAC_SIZE bytes, got ${mac.size}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedEnvelope) return false
        return schemaVersion == other.schemaVersion &&
            cipherSuiteId == other.cipherSuiteId &&
            nonce.contentEquals(other.nonce) &&
            recipients == other.recipients &&
            ciphertext.contentEquals(other.ciphertext) &&
            mac.contentEquals(other.mac) &&
            metadataEquals(metadata, other.metadata)
    }

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + cipherSuiteId.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + recipients.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + mac.contentHashCode()
        result = 31 * result + metadata.keys.sorted().joinToString(",").hashCode()
        return result
    }

    private fun metadataEquals(a: Map<String, ByteArray>, b: Map<String, ByteArray>): Boolean {
        if (a.size != b.size) return false
        for ((k, v) in a) {
            val bv = b[k] ?: return false
            if (!v.contentEquals(bv)) return false
        }
        return true
    }
}
