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
)

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
