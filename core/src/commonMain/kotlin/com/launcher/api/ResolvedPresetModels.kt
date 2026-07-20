package com.launcher.api

import com.launcher.wire.WireVersion

/**
 * Resolved preset view for UI and modules. Renamed from `ProfileSnapshot`
 * in TASK-65 (T640) — `Profile` namespace now means the per-device live
 * state ([com.launcher.api.profile.ProfileData]), and this type is the
 * resolved view of a preset against the module graph.
 */
data class ResolvedPresetSnapshot(
    val schemaVersion: WireVersion,
    val id: String,
    val moduleFlags: Map<String, Boolean>,
    val accessibilityPreset: String?,
    val layoutHints: Map<String, String>,
) {
    companion object {
        /**
         * Version constants for the bootstrap-profile document (`default_profile.json`, read by
         * `ProfileEngine`) per `docs/architecture/wire-format.md` §11.
         *
         * This is a **separate wire format** from [com.launcher.preset.model.Profile], despite
         * both being called "profile". `Profile` is the ECS preset document at "2.0"; this one is
         * the module-flags bootstrap snapshot parsed by hand with `org.json`. The two carried
         * independent constants (`Profile.CURRENT_SCHEMA_VERSION = 2` versus a private
         * `SUPPORTED_SCHEMA = 1` in `ProfileEngine`), which read as version drift in the TASK-138
         * inventory. It was not drift — they are unrelated documents, and the constants are named
         * per format here so the next reader is not misled the same way.
         */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** Module flags and layout hints are additive maps; unknown keys carry no meaning loss. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** A bundled asset, never written back by the app. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}

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
