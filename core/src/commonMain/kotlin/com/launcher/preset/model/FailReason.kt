package com.launcher.preset.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Why a [Component] could not be applied.
 *
 * TASK-136: made `@Serializable`. `FailReason` now rides on
 * [LifecycleState.Failed], which is a `Component` in the entity bag and therefore
 * part of the `Profile` wire format — so every subtype needs a stable
 * `@SerialName` discriminator (polymorphic, `classDiscriminator = "type"`).
 */
@Serializable
sealed class FailReason {
    @Serializable @SerialName("PermissionDenied")
    data class PermissionDenied(val permission: String) : FailReason()

    @Serializable @SerialName("PolicyBlocked")
    data class PolicyBlocked(val policy: String) : FailReason()

    @Serializable @SerialName("NetworkUnavailable")
    data object NetworkUnavailable : FailReason()

    @Serializable @SerialName("Cancelled")
    data object Cancelled : FailReason()

    @Serializable @SerialName("PairingNotEstablished")
    data object PairingNotEstablished : FailReason()

    @Serializable @SerialName("InternalError")
    data class InternalError(
        val messageKey: String,
        val args: Map<String, String> = emptyMap(),
    ) : FailReason()

    fun toI18nKey(): String = when (this) {
        is PermissionDenied -> "outcome.failed.permission_denied"
        is PolicyBlocked -> "outcome.failed.policy_blocked"
        NetworkUnavailable -> "outcome.failed.network_unavailable"
        Cancelled -> "outcome.failed.cancelled"
        PairingNotEstablished -> "outcome.failed.pairing_not_established"
        is InternalError -> messageKey
    }
}
