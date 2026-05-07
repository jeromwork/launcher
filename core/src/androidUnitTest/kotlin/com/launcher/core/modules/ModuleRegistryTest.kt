package com.launcher.core.modules

import com.launcher.api.ContractRequirement
import com.launcher.api.ModuleDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleRegistryTest {

    @Test
    fun moduleDegradedWhenRequiredMajorExceedsCore() {
        val registry = ModuleRegistry(
            listOf(
                ModuleDescriptor(
                    moduleId = "future-contract-consumer",
                    requiredContracts = setOf(
                        ContractRequirement("launcher.events", minimumMajorVersion = 99),
                    ),
                    publishedSurfaces = emptySet(),
                ),
            ),
        )
        val states = registry.registeredModules.value
        assertEquals(1, states.size)
        assertFalse(states[0].contractSatisfied)
        assertTrue(states[0].degraded)
        val resolution = registry.resolutionStates().single()
        assertEquals("future-contract-consumer", resolution.moduleId)
        assertFalse(resolution.contractSatisfied)
    }

    @Test
    fun moduleEnabledWhenRequirementsSatisfied() {
        val registry = ModuleRegistry(
            listOf(
                ModuleDescriptor(
                    moduleId = "minimal",
                    requiredContracts = setOf(
                        ContractRequirement("launcher.events", minimumMajorVersion = 1),
                    ),
                    publishedSurfaces = emptySet(),
                ),
            ),
        )
        val states = registry.registeredModules.value.single()
        assertTrue(states.contractSatisfied)
        assertFalse(states.degraded)
    }
}
