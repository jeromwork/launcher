package com.launcher.api

import com.launcher.api.action.ProviderId

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

    /**
     * Diagnostic emit from spec 002 communication shell. Removed in spec 005 Phase 6
     * (replaced by [ActionDispatched]); retained here only until Phase 6 deletion lands.
     */
    @Deprecated("Removed in spec 005 Phase 6 — use ActionDispatched instead.")
    data class CommunicationDiagnostic(
        val eventType: CommunicationDiagnosticEventType,
        val actionCycleRef: String? = null,
        val tileRef: String? = null,
        val actionType: CommunicationActionType? = null,
        val reasonCode: String? = null,
    ) : ProjectEvent()

    /**
     * Emitted exactly once per top-level `ActionDispatcher.dispatch()` call (NOT per
     * fallback recursion). Replaces [CommunicationDiagnostic] from spec 002.
     *
     * **Field set is frozen** per [`contracts/diagnostics-events-v2.md`](specs/005-action-architecture-v2/contracts/diagnostics-events-v2.md).
     * Adding a field requires a major-bump and explicit security review per
     * Article XIV §3 (no PII allowed): provider category and outcome category
     * are not PII; phone numbers, contact refs, URLs, payload contents must
     * never leak into events. Konsist test `EventTaxonomyTest` enforces this.
     */
    data class ActionDispatched(
        val providerId: ProviderId,
        val resultKind: ResultKind,
        val fallbackUsed: Boolean,
        val timestampMs: Long,
    ) : ProjectEvent() {

        enum class ResultKind {
            Ok,
            BlockedByPolicy,
            ProviderUnavailable,
            Failure,
        }
    }
}

enum class PackageChangeReason {
    PACKAGE_ADDED,
    PACKAGE_REMOVED,
    PACKAGE_REPLACED,
    PACKAGE_CHANGED,
    MY_PACKAGE_REPLACED,
}
