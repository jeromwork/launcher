package com.launcher.core.actions

import com.launcher.api.CapabilityState
import com.launcher.api.ControlMode
import com.launcher.api.EscapeHandlingDecision
import com.launcher.api.EscapeProtectionPolicy
import com.launcher.api.EscapeVector
import com.launcher.api.SafetyCapability

class EscapeProtectionPolicyEngine(
    private val capabilityResolver: CapabilitySnapshotResolver,
) {
    fun currentPolicy(): EscapeProtectionPolicy {
        val snapshot = capabilityResolver.resolve()
        val mode = snapshot.controlMode
        val hasAccessibility = snapshot.stateOf(SafetyCapability.ACCESSIBILITY_SERVICE) == CapabilityState.GRANTED
        val hasUsage = snapshot.stateOf(SafetyCapability.USAGE_ACCESS) == CapabilityState.GRANTED
        val hasDeviceOwner = snapshot.stateOf(SafetyCapability.DEVICE_OWNER) == CapabilityState.GRANTED
        val hasStatusBarRestriction =
            snapshot.stateOf(SafetyCapability.STATUS_BAR_RESTRICTION) == CapabilityState.GRANTED
        val hasLockTask = snapshot.stateOf(SafetyCapability.LOCK_TASK) == CapabilityState.GRANTED

        return EscapeProtectionPolicy(
            mode = mode,
            canInterceptBack = true,
            canRecoverFromHomeExit = hasAccessibility || hasUsage,
            canRestrictShade = mode == ControlMode.STRICT && hasStatusBarRestriction,
            canRestrictRecents = mode == ControlMode.STRICT && (hasLockTask || hasDeviceOwner),
        )
    }

    fun decisionFor(vector: EscapeVector): EscapeHandlingDecision {
        val policy = currentPolicy()
        return when (vector) {
            EscapeVector.BACK -> EscapeHandlingDecision(
                shouldAttemptRecovery = true,
                isGuaranteeLevel = true,
                reasonCode = "back_intercepted_by_launcher",
            )
            EscapeVector.HOME -> EscapeHandlingDecision(
                shouldAttemptRecovery = policy.canRecoverFromHomeExit,
                isGuaranteeLevel = policy.mode == ControlMode.STRICT && policy.canRecoverFromHomeExit,
                reasonCode = if (policy.canRecoverFromHomeExit) "home_recover_possible" else "home_recover_limited",
            )
            EscapeVector.SYSTEM_SHADE -> EscapeHandlingDecision(
                shouldAttemptRecovery = policy.canRestrictShade,
                isGuaranteeLevel = policy.canRestrictShade,
                reasonCode = if (policy.canRestrictShade) "shade_restricted" else "shade_not_restricted",
            )
            EscapeVector.RECENTS -> EscapeHandlingDecision(
                shouldAttemptRecovery = policy.canRestrictRecents,
                isGuaranteeLevel = policy.canRestrictRecents,
                reasonCode = if (policy.canRestrictRecents) "recents_restricted" else "recents_not_restricted",
            )
            EscapeVector.UNKNOWN -> EscapeHandlingDecision(
                shouldAttemptRecovery = policy.canRecoverFromHomeExit,
                isGuaranteeLevel = false,
                reasonCode = "unknown_vector_best_effort",
            )
        }
    }
}

