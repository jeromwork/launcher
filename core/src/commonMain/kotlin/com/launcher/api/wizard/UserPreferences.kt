package com.launcher.api.wizard

import kotlinx.serialization.Serializable

/**
 * Persistent UX preferences captured during the wizard.
 *
 * Wire format — schemaVersion=1 — per CLAUDE.md rule 5. Persisted via
 * [UserPreferencesStore] (DataStore on Android, in-memory in tests).
 *
 * TODO(server-roadmap): future cloud sync target — spec 008 ConfigDocument
 * `userPreferences` slot. F-4 + cloud sync required first.
 *
 * TODO(shareability): future cross-app target — shared ContentProvider when
 * messenger ecosystem app materialises (per C-31).
 */
@Serializable
data class UserPreferences(
    val schemaVersion: Int = 1,
    val theme: ThemeChoice = ThemeChoice.Auto,
    val fontScale: Float? = null,
    val languageOverride: String? = null,
    val attestedSettings: Map<String, AttestationRecord> = emptyMap(),
    val wizardCompletedAppFamilies: Set<String> = emptySet(),
) {
    /**
     * Has the user expressed a value for the given UIChoice step refId?
     * Used by [com.launcher.api.wizard.WizardEngine.computePending] to skip
     * UIChoice steps whose answer is already persisted (TASK-7 / FR-013).
     *
     * Hardcoded key set covers the current UIChoice refIds (`theme`,
     * `fontScale`, `language`); unknown refIds fall through to
     * [attestedSettings] lookup so SystemSetting self-attestation also works.
     *
     * TODO(TASK-?): generalize to a `Map<refId, JsonElement>` once UI pool
     * supports custom UIChoice variants beyond the trio.
     */
    fun hasValueFor(refId: String): Boolean = when (refId) {
        "theme" -> theme != ThemeChoice.Auto || refId in attestedSettings
        "fontScale" -> fontScale != null
        "language" -> languageOverride != null
        else -> attestedSettings.containsKey(refId)
    }
}

@Serializable
enum class ThemeChoice { Light, Dark, Auto }

/**
 * Self-attestation record for a system setting that has no programmatic
 * detection on the device (per FR-053a `Indeterminate → SelfAttest`).
 *
 * `attestedAt` is epoch-millis to match the existing project convention
 * (see DocSnapshot.kt) — kotlinx-datetime is not in the project today.
 */
@Serializable
data class AttestationRecord(
    val attestedAtEpochMillis: Long,
    val value: Boolean,
)
