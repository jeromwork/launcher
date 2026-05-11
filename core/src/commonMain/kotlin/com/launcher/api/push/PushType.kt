package com.launcher.api.push

/**
 * Discriminator for the FCM data-message `type` field
 * (contracts/fcm-payload.md). New entries are **additive** and old Managed
 * apps drop unknown types gracefully (FcmPayloadWireFormat).
 *
 * Modelled as a sealed type (not enum) so future specs can attach
 * type-specific data classes without widening this base file.
 *
 * TODO(future): add `IncomingCall` for the jitsi calls spec — it will
 * reuse the same Worker/payload-format infrastructure (plan.md §Reusable
 * trust primitive).
 */
sealed interface PushType {
    val wireValue: String

    data object ConfigChanged : PushType { override val wireValue: String = "config-changed" }
    data object CommandIssued : PushType { override val wireValue: String = "command-issued" }
    data object Revoke : PushType { override val wireValue: String = "revoke" }

    companion object {
        fun fromWireOrNull(value: String?): PushType? = when (value) {
            ConfigChanged.wireValue -> ConfigChanged
            CommandIssued.wireValue -> CommandIssued
            Revoke.wireValue -> Revoke
            else -> null
        }
    }
}
