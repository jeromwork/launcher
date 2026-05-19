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

    /**
     * Spec 010 T073 / FR-020b — emitted when a [com.launcher.api.setup.SetupCheck.check]
     * invocation throws an exception (typically Xiaomi MIUI `SecurityException`
     * on `PowerManager.isIgnoringBatteryOptimizations`, see [TODO-SPEC010-EMU-009]
     * + R5 risk).
     *
     * Categorical contents only — no PII. The [reason] is the exception's
     * `message` string truncated to 200 chars (which is implementation-controlled
     * по контракту: never contains user identifiers).
     */
    data class SetupCheckException(
        val checkId: String,
        val reason: String,
        val timestampMs: Long,
    ) : ProjectEvent()
}

enum class PackageChangeReason {
    PACKAGE_ADDED,
    PACKAGE_REMOVED,
    PACKAGE_REPLACED,
    PACKAGE_CHANGED,
    MY_PACKAGE_REPLACED,
}
