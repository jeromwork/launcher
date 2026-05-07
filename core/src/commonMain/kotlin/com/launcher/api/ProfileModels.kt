package com.launcher.api

/**
 * Immutable profile view for UI and modules. See data-model.md — Profile.
 */
data class ProfileSnapshot(
    val schemaVersion: Int,
    val id: String,
    val moduleFlags: Map<String, Boolean>,
    val accessibilityPreset: String?,
    val layoutHints: Map<String, String>,
)

/**
 * Explains non-default or degraded resolution (data-model.md — Safe fallback and degradation record).
 */
data class DegradationRecord(
    val activeProfileId: String,
    val degradedModules: List<String>,
    val reasonCodes: List<DegradationReason>,
)

enum class DegradationReason {
    RUNTIME_SAFETY_FALLBACK,
    CONTRACT_INCOMPATIBLE,
    MODULE_UNAVAILABLE,
    PERMISSION_CONSTRAINED,
    PROFILE_OVERRIDDEN_BY_RESOLUTION,
    INVALID_PROFILE_FALLBACK,
    PARTIAL_PROFILE_MERGED,
}

/**
 * Profile after validation and composition against module graph.
 */
data class EffectiveProfile(
    val snapshot: ProfileSnapshot,
    val profileGeneration: Int,
    val effectiveModuleFlags: Map<String, Boolean>,
    val degradation: DegradationRecord,
)
