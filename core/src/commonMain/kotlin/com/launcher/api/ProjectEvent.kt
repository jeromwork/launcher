package com.launcher.api

/**
 * Normalized project-level events after [com.launcher.core.bridge.SystemEventBridge] and Core services.
 * Contract: `launcher.events` v1 — see specs contracts/project-events.md.
 */
sealed class ProjectEvent {
    /**
     * Install/update/remove affecting launcher-visible packages. Payload is minimal; consumers re-query AppIndex.
     */
    data class PackageSetChanged(
        val reason: PackageChangeReason? = null,
    ) : ProjectEvent()

    data class ProfileChanged(
        val profileGeneration: Int,
    ) : ProjectEvent()

    data class ModuleGraphChanged(
        val affectedModuleIds: List<String>,
    ) : ProjectEvent()

    data class CommunicationDiagnostic(
        val eventType: CommunicationDiagnosticEventType,
        val actionCycleRef: String? = null,
        val tileRef: String? = null,
        val actionType: CommunicationActionType? = null,
        val reasonCode: String? = null,
    ) : ProjectEvent()
}

enum class PackageChangeReason {
    PACKAGE_ADDED,
    PACKAGE_REMOVED,
    PACKAGE_REPLACED,
    PACKAGE_CHANGED,
    MY_PACKAGE_REPLACED,
}
