package com.launcher.api

/**
 * Declares a first-party module for [com.launcher.core.modules.ModuleRegistry].
 * Contract: launcher.modules v1.
 */
data class ModuleDescriptor(
    val moduleId: String,
    val requiredContracts: Set<ContractRequirement>,
    val publishedSurfaces: Set<String>,
)

/**
 * Required major version for a contract id (data-model.md — Published contract).
 */
data class ContractRequirement(
    val contractId: String,
    val minimumMajorVersion: Int,
)
