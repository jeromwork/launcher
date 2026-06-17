package family.crypto.api.values

import kotlinx.datetime.Instant

/** Stub-only domain type for spec 017 (ADR-008 social recovery). */
class EscrowBundle(
    val schemaVersion: Int,
    val externalId: String,
    val encryptedPayload: ByteArray,
    val createdAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EscrowBundle) return false
        if (schemaVersion != other.schemaVersion) return false
        if (externalId != other.externalId) return false
        if (!encryptedPayload.contentEquals(other.encryptedPayload)) return false
        if (createdAt != other.createdAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + externalId.hashCode()
        result = 31 * result + encryptedPayload.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
