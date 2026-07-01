package com.launcher.api

/**
 * Resolved preset view for UI and modules. Renamed from `ProfileSnapshot`
 * in TASK-65 (T640) — `Profile` namespace now means the per-device live
 * state ([com.launcher.api.profile.ProfileData]), and this type is the
 * resolved view of a preset against the module graph.
 */
data class ResolvedPresetSnapshot(
    val schemaVersion: Int,
    val id: String,
    val moduleFlags: Map<String, Boolean>,
    val accessibilityPreset: String?,
    val layoutHints: Map<String, String>,
)

/**
 * Explains non-default or degraded resolution.
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
 * Preset after validation and composition against module graph. Renamed from
 * `EffectiveProfile` in TASK-65 (T640).
 */
data class EffectivePreset(
    val snapshot: ResolvedPresetSnapshot,
    val profileGeneration: Int,
    val effectiveModuleFlags: Map<String, Boolean>,
    val degradation: DegradationRecord,
)
