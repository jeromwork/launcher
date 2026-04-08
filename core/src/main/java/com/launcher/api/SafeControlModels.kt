package com.launcher.api

enum class ControlMode {
    STRICT,
    STANDARD,
}

enum class CapabilityState {
    GRANTED,
    MISSING,
    LIMITED,
}

enum class SafetyCapability {
    DEFAULT_HOME,
    ACCESSIBILITY_SERVICE,
    USAGE_ACCESS,
    OVERLAY,
    BATTERY_EXEMPTION,
    DEVICE_OWNER,
    LOCK_TASK,
    STATUS_BAR_RESTRICTION,
}

data class CapabilityStatus(
    val capability: SafetyCapability,
    val state: CapabilityState,
    val reasonCode: String? = null,
)

data class CapabilitySnapshot(
    val controlMode: ControlMode,
    val statuses: List<CapabilityStatus>,
) {
    fun stateOf(capability: SafetyCapability): CapabilityState =
        statuses.firstOrNull { it.capability == capability }?.state ?: CapabilityState.MISSING
}

data class AllowedAppsPolicy(
    val allowedPackages: Set<String>,
    val alwaysAllowedPackages: Set<String> = emptySet(),
    val blockedPackages: Set<String> = emptySet(),
) {
    fun isPackageAllowed(packageName: String): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        if (packageName in blockedPackages) {
            return false
        }
        return packageName in alwaysAllowedPackages || packageName in allowedPackages
    }

    companion object {
        fun permissive(): AllowedAppsPolicy = AllowedAppsPolicy(
            allowedPackages = setOf("*"),
        )
    }
}

enum class EscapeVector {
    BACK,
    HOME,
    SYSTEM_SHADE,
    RECENTS,
    UNKNOWN,
}

data class EscapeProtectionPolicy(
    val mode: ControlMode,
    val canInterceptBack: Boolean,
    val canRecoverFromHomeExit: Boolean,
    val canRestrictShade: Boolean,
    val canRestrictRecents: Boolean,
)

data class EscapeHandlingDecision(
    val shouldAttemptRecovery: Boolean,
    val isGuaranteeLevel: Boolean,
    val reasonCode: String,
)

enum class SafetyDiagnosticEventType {
    ESCAPE_ATTEMPT_DETECTED,
    ESCAPE_BLOCKED,
    ESCAPE_RECOVERED_TO_HOME,
    CAPABILITY_MISSING,
}

