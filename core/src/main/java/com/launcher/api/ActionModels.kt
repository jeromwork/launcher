package com.launcher.api

/**
 * User- or system-initiated operations (contracts/actions.md).
 */
sealed class ActionRequest {
    data class OpenApplication(
        val catalogStableKey: String,
        val sourceModuleId: String? = null,
    ) : ActionRequest()

    data class OpenSystemSettings(
        val target: SystemSettingsTarget = SystemSettingsTarget.General,
        val sourceModuleId: String? = null,
    ) : ActionRequest()
}

enum class SystemSettingsTarget {
    General,
}

sealed class DispatchResult {
    data object Ok : DispatchResult()

    data class BlockedByPolicy(
        val reason: BlockReason,
    ) : DispatchResult()

    data class Failure(
        val reason: String,
    ) : DispatchResult()
}

enum class BlockReason {
    NOT_IN_CATALOG,
    NOT_LAUNCHABLE,
    INVALID_REQUEST,
    PERMISSION_OR_POLICY,
}
