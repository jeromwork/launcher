package com.launcher.app

import com.launcher.api.ContractRequirement
import com.launcher.api.ModuleDescriptor
import com.launcher.core.contracts.CoreContractVersions

/**
 * Static list of first-party module descriptors passed into [LauncherCore][com.launcher.core.LauncherCore].
 * Real features add a `:feature-*` module and register a descriptor here (see EXTENSION_GUIDE.md).
 */
object AppModuleDescriptors {

    val all: List<ModuleDescriptor> = listOf(
        ModuleDescriptor(
            moduleId = "app.placeholder",
            requiredContracts = setOf(
                ContractRequirement(CoreContractVersions.LAUNCHER_EVENTS, 1),
                ContractRequirement(CoreContractVersions.LAUNCHER_APPINDEX, 1),
                ContractRequirement(CoreContractVersions.LAUNCHER_ACTIONS, 1),
            ),
            publishedSurfaces = emptySet(),
        ),
    )
}
