package com.launcher.api.health

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
@Serializable
data class Health(
    val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
    val batteryPercent: Int,
    val charging: Boolean,
    val connectivity: Connectivity,
    val ringerVolumePercent: Int,
    val audioStreamMuted: Boolean,
    val lastSeen: Long,
    val appVersion: String,
) {
    companion object {
        /** Wire-format version recognised by this build. Reader does NOT throw on > this (FR-043). */
        const val SUPPORTED_SCHEMA_VERSION: Int = 1
    }
}
