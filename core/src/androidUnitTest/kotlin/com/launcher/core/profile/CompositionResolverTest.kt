package com.launcher.core.profile

import com.launcher.api.DegradationReason
import com.launcher.api.ProfileSnapshot
import com.launcher.core.modules.ModuleResolutionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionResolverTest {

    @Test
    fun contractIncompatibilityTakesPrecedenceOverAbsentFromBuild() {
        val raw = ProfileSnapshot(1, "p", mapOf("m" to true), null, emptyMap())
        val states = listOf(
            ModuleResolutionState(
                moduleId = "m",
                presentInBuild = false,
                contractSatisfied = false,
            ),
        )
        val eff = CompositionResolver.resolve(raw, profileGeneration = 1, states)
        assertEquals(false, eff.effectiveModuleFlags["m"])
        assertTrue(eff.degradation.reasonCodes.contains(DegradationReason.CONTRACT_INCOMPATIBLE))
        assertFalse(eff.degradation.reasonCodes.contains(DegradationReason.MODULE_UNAVAILABLE))
    }

    @Test
    fun moduleUnavailableWhenPresentButContractSatisfiedAndMissingFromGraph() {
        val raw = ProfileSnapshot(1, "p", mapOf("ghost" to true), null, emptyMap())
        val eff = CompositionResolver.resolve(raw, 1, emptyList())
        assertEquals(false, eff.effectiveModuleFlags["ghost"])
        assertTrue(eff.degradation.reasonCodes.contains(DegradationReason.MODULE_UNAVAILABLE))
    }

    @Test
    fun reflectsProfileWhenModuleHealthy() {
        val raw = ProfileSnapshot(
            1,
            "p",
            mapOf("a" to true, "b" to false),
            null,
            emptyMap(),
        )
        val states = listOf(
            ModuleResolutionState("a", presentInBuild = true, contractSatisfied = true),
            ModuleResolutionState("b", presentInBuild = true, contractSatisfied = true),
        )
        val eff = CompositionResolver.resolve(raw, 2, states)
        assertEquals(true, eff.effectiveModuleFlags["a"])
        assertEquals(false, eff.effectiveModuleFlags["b"])
        assertTrue(eff.degradation.reasonCodes.isEmpty())
        assertTrue(eff.degradation.degradedModules.isEmpty())
    }

    @Test
    fun moduleUnavailableDisablesWhenProfileWantsButNotInBuild() {
        val raw = ProfileSnapshot(1, "p", mapOf("m" to true), null, emptyMap())
        val states = listOf(
            ModuleResolutionState("m", presentInBuild = false, contractSatisfied = true),
        )
        val eff = CompositionResolver.resolve(raw, 1, states)
        assertEquals(false, eff.effectiveModuleFlags["m"])
        assertTrue(eff.degradation.reasonCodes.contains(DegradationReason.MODULE_UNAVAILABLE))
    }
}
