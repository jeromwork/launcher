package com.launcher.core.actions

import com.launcher.api.CapabilitySnapshot
import com.launcher.api.CapabilityState
import com.launcher.api.CapabilityStatus
import com.launcher.api.ControlMode
import com.launcher.api.EscapeVector
import com.launcher.api.SafetyCapability
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscapeProtectionPolicyEngineTest {

    @Test
    fun strictModeWithCapabilitiesHasRecoverDecisionForHome() {
        val resolver = mockk<CapabilitySnapshotResolver>()
        every { resolver.resolve() } returns CapabilitySnapshot(
            controlMode = ControlMode.STRICT,
            statuses = listOf(
                CapabilityStatus(SafetyCapability.ACCESSIBILITY_SERVICE, CapabilityState.GRANTED),
                CapabilityStatus(SafetyCapability.USAGE_ACCESS, CapabilityState.GRANTED),
                CapabilityStatus(SafetyCapability.DEVICE_OWNER, CapabilityState.GRANTED),
                CapabilityStatus(SafetyCapability.LOCK_TASK, CapabilityState.GRANTED),
                CapabilityStatus(SafetyCapability.STATUS_BAR_RESTRICTION, CapabilityState.GRANTED),
            ),
        )
        val engine = EscapeProtectionPolicyEngine(resolver)

        val decision = engine.decisionFor(EscapeVector.HOME)

        assertTrue(decision.shouldAttemptRecovery)
        assertTrue(decision.isGuaranteeLevel)
    }

    @Test
    fun standardModeWithoutCapabilitiesIsBestEffortOnly() {
        val resolver = mockk<CapabilitySnapshotResolver>()
        every { resolver.resolve() } returns CapabilitySnapshot(
            controlMode = ControlMode.STANDARD,
            statuses = listOf(
                CapabilityStatus(SafetyCapability.ACCESSIBILITY_SERVICE, CapabilityState.MISSING),
                CapabilityStatus(SafetyCapability.USAGE_ACCESS, CapabilityState.MISSING),
                CapabilityStatus(SafetyCapability.DEVICE_OWNER, CapabilityState.MISSING),
                CapabilityStatus(SafetyCapability.LOCK_TASK, CapabilityState.LIMITED),
                CapabilityStatus(SafetyCapability.STATUS_BAR_RESTRICTION, CapabilityState.LIMITED),
            ),
        )
        val engine = EscapeProtectionPolicyEngine(resolver)

        val decision = engine.decisionFor(EscapeVector.SYSTEM_SHADE)

        assertFalse(decision.shouldAttemptRecovery)
        assertFalse(decision.isGuaranteeLevel)
    }
}

