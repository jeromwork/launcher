package family.keys.api

/**
 * One recipient of an encrypted envelope: a device whose X25519 public key is known
 * and whose [DeviceId] is used as the routing key in [Envelope.recipientKeys].
 *
 * Constructed by [family.keys.api.RecipientResolver] when assembling the recipient
 * list for an outgoing [Envelope]. Internal to the keys / crypto layer — caller-side
 * app code never sees `RecipientPubKey` directly (it interacts only with
 * [RemoteStorage]).
 *
 * @property deviceId Routing key (which slot inside [Envelope.recipientKeys] this
 *   recipient owns).
 * @property pubKey 32-byte X25519 public key; used as the sealed-box recipient by
 *   [family.crypto.api.AsymmetricCrypto.sealForRecipient].
 */
data class RecipientPubKey(
    val deviceId: DeviceId,
    val pubKey: ByteArray
) {
    init {
        require(pubKey.size == X25519_PUBKEY_SIZE) {
            "pubKey size ${pubKey.size} != $X25519_PUBKEY_SIZE (X25519 raw)"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecipientPubKey) return false
        if (deviceId != other.deviceId) return false
        if (!pubKey.contentEquals(other.pubKey)) return false
        return true
    }

    override fun hashCode(): Int = 31 * deviceId.hashCode() + pubKey.contentHashCode()

    companion object {
        const val X25519_PUBKEY_SIZE: Int = 32
    }
}
