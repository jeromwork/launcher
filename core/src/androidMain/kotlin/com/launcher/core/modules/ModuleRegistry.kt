package com.launcher.core.modules

import com.launcher.api.ModuleDescriptor
import com.launcher.core.contracts.CoreContractVersions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ModuleRuntimeState(
    val descriptor: ModuleDescriptor,
    val contractSatisfied: Boolean,
    val degraded: Boolean,
)

data class ModuleResolutionState(
    val moduleId: String,
    val presentInBuild: Boolean,
    val contractSatisfied: Boolean,
)

/**
 * Validates [ModuleDescriptor.requiredContracts] against Core majors (contracts/module-registration.md).
 */
class ModuleRegistry(
    initialDescriptors: List<ModuleDescriptor> = emptyList(),
) {
    private val _registeredModules = MutableStateFlow<List<ModuleRuntimeState>>(emptyList())
    val registeredModules: StateFlow<List<ModuleRuntimeState>> = _registeredModules.asStateFlow()

    init {
        setDescriptors(initialDescriptors)
    }

    fun setDescriptors(descriptors: List<ModuleDescriptor>) {
        _registeredModules.value = descriptors.map { d ->
            val satisfied = d.requiredContracts.all { req ->
                val provided = CoreContractVersions.providedMajor(req.contractId)
                provided != null && provided >= req.minimumMajorVersion
            }
            ModuleRuntimeState(
                descriptor = d,
                contractSatisfied = satisfied,
                degraded = !satisfied,
            )
        }
    }

    fun resolutionStates(): List<ModuleResolutionState> =
        _registeredModules.value.map { m ->
            ModuleResolutionState(
                moduleId = m.descriptor.moduleId,
                presentInBuild = true,
                contractSatisfied = m.contractSatisfied,
            )
        }
}
