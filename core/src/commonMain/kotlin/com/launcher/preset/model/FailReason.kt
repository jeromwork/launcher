package com.launcher.preset.model

sealed class FailReason {
    data class PermissionDenied(val permission: String) : FailReason()
    data class PolicyBlocked(val policy: String) : FailReason()
    object NetworkUnavailable : FailReason()
    object Cancelled : FailReason()
    object PairingNotEstablished : FailReason()
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
