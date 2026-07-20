package com.launcher.api.health

import com.launcher.wire.WireVersion
import com.launcher.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Per-device health snapshot — "how this device feels right now".
 *
 * Wire-format root per [`contracts/health-wire-format.md`](specs/006-provider-capabilities-and-health/contracts/health-wire-format.md)
 * v1.0.0. Persisted in app-private DataStore today, exported to Firestore
 * `/links/{linkId}/health` in spec 007. Pure data — no logic of how this
 * snapshot is acted upon (banner derivation lives in `AlertBannerStateProvider`,
 * cross-device offline reactions live in spec 013).
 *
 * Field semantics:
 *  - [batteryPercent] 0..100 (clamped by adapter); 0 if reading failed.
 *  - [charging] false if unknown.
 *  - [connectivity] use [Connectivity.fromWireOrNone] when parsing untrusted
 *    sources; unknown values map to [Connectivity.None].
 *  - [ringerVolumePercent] 0..100 normalised, NOT raw `STREAM_RING` units
 *    (adapter normalises against `getStreamMaxVolume(STREAM_RING)`).
 *  - [audioStreamMuted] reflects effective state: true if `STREAM_RING == 0`
 *    OR system DND active suppressing ringer (FR-016).
 *  - [lastSeen] epoch millis of most recent foreground (RESUMED) event.
 *  - [appVersion] launcher's own `BuildConfig.VERSION_NAME`.
 */
// @EncodeDefault: this format encodes with `encodeDefaults = false`, and I1 requires the version
// fields on every document.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Health(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val batteryPercent: Int,
    val charging: Boolean,
    val connectivity: Connectivity,
    val ringerVolumePercent: Int,
    val audioStreamMuted: Boolean,
    val lastSeen: Long,
    val appVersion: String,
) : WireVersionHeader {
    companion object {
        /** What this build writes. Was the integer `1` before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** Nothing in this shape needs a newer reader; raise only on a meaning change (§3). */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** A snapshot is rewritten wholesale by the device that owns it, never merged. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}
