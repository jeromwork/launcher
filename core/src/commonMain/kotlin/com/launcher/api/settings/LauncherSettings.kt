package com.launcher.api.settings

import com.launcher.wire.WireVersion
import com.launcher.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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
 * added as additive optional fields — a MINOR bump of [SCHEMA_VERSION] with the reader and
 * writer minimums left alone (`docs/architecture/wire-format.md` §3).
 */
// @EncodeDefault: this format encodes with `encodeDefaults = false`, and I1 requires the version
// fields on every document.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LauncherSettings(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val banners: BannerToggles = BannerToggles(),
) : WireVersionHeader {
    companion object {
        /** What this build writes. Was the integer `1` before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /**
         * The reserved spec-013 field names below land as additive optional fields, which by §3
         * raise MINOR only — an old reader ignoring them still behaves correctly.
         */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /**
         * Settings are synced (spec 008 `/config`), so a device could read and write back a
         * document authored elsewhere. Raise this once a field appears that an older writer would
         * silently drop, unless unknown-field preservation (§6) lands first.
         */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)

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
