package com.launcher.api.settings

import kotlinx.serialization.Serializable

/**
 * User-toggleable settings for spec 006 banner alerts.
 *
 * Wire-format root per [`contracts/launcher-settings-wire-format.md`](specs/006-provider-capabilities-and-health/contracts/launcher-settings-wire-format.md)
 * v1.0.0. Persisted in app-private DataStore; future cloud sync via spec 008
 * `/config` is non-breaking (this wire-format already has `schemaVersion`).
 *
 * Reserved field names anticipated by spec 013 (offline detection):
 * `banners.offline`, `raiseRingerOnLongOffline`, `escalation.firstStepMinutes`,
 * `escalation.subsequentStepMinutes`, `escalation.stepPercent`. They will be
 * added as additive optional fields without bumping [SUPPORTED_SCHEMA_VERSION].
 */
@Serializable
data class LauncherSettings(
    val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
    val banners: BannerToggles = BannerToggles(),
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 1

        /**
         * Defaults applied when DataStore is empty OR corrupted (FR-051).
         *
         * Senior preset (`simple-launcher`) gets both banners ON — the safety
         * net is the whole point for that audience. Other presets default OFF
         * — power users opt in.
         */
        fun defaultsForPreset(presetSlug: String): LauncherSettings = when (presetSlug) {
            "simple-launcher" -> LauncherSettings(banners = BannerToggles(airplane = true, mute = true))
            else              -> LauncherSettings(banners = BannerToggles(airplane = false, mute = false))
        }
    }
}

@Serializable
data class BannerToggles(
    val airplane: Boolean = false,
    val mute: Boolean = false,
)
