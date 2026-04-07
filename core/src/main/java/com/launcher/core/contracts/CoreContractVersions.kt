package com.launcher.core.contracts

/**
 * Majors provided by this Core build for [com.launcher.core.modules.ModuleRegistry] checks.
 */
object CoreContractVersions {
    const val LAUNCHER_EVENTS = "launcher.events"
    const val LAUNCHER_PROFILE = "launcher.profile"
    const val LAUNCHER_MODULES = "launcher.modules"
    const val LAUNCHER_APPINDEX = "launcher.appindex"
    const val LAUNCHER_ACTIONS = "launcher.actions"
    const val LAUNCHER_COMMUNICATION_WHATSAPP_HANDOFF = "launcher.communication.whatsapp.handoff"
    const val LAUNCHER_COMMUNICATION_RETURN_CONTEXT = "launcher.communication.return-context"
    const val LAUNCHER_COMMUNICATION_DIAGNOSTICS = "launcher.communication.diagnostics"

    private val majors: Map<String, Int> = mapOf(
        LAUNCHER_EVENTS to 1,
        LAUNCHER_PROFILE to 1,
        LAUNCHER_MODULES to 1,
        LAUNCHER_APPINDEX to 1,
        LAUNCHER_ACTIONS to 1,
        LAUNCHER_COMMUNICATION_WHATSAPP_HANDOFF to 1,
        LAUNCHER_COMMUNICATION_RETURN_CONTEXT to 1,
        LAUNCHER_COMMUNICATION_DIAGNOSTICS to 1,
    )

    fun providedMajor(contractId: String): Int? = majors[contractId]
}
