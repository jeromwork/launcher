package family.keys.api

import kotlin.jvm.JvmInline

/**
 * Stable identifier for a single physical device within a user's namespace.
 *
 * Allocated when the user signs in on a device for the first time; persists across
 * app restarts (stored in DataStore). Unique within the scope of a single UID,
 * not globally unique. Different UIDs on the same physical device produce different
 * DeviceIds (because each user gets a fresh registration).
 *
 * Used as the key inside [Envelope.recipientKeys] to identify which device's
 * encrypted CEK is which. Survives app updates, does NOT survive uninstall.
 */
@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotEmpty()) { "DeviceId must not be empty" }
        require(value.length <= 128) { "DeviceId max 128 chars (Firestore field name limits)" }
    }
}
