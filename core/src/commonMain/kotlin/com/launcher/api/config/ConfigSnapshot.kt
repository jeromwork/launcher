package com.launcher.api.config

import com.launcher.wire.WireVersion
import com.launcher.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName

import kotlinx.serialization.Serializable

/**
 * Immutable historical record of a `/config/current` state — written every
 * time an admin pushes from EditorScreen (spec 009 FR-036). Persisted in
 * Firestore subcollection `/links/{linkId}/config/history/{autoId}`. Pure
 * data; no behaviour.
 *
 * Dual schemaVersion (envelope + nested config) per plan.md §11 C-2 +
 * research.md R-002 — independent evolution: envelope changes (e.g.
 * adding [recordedFromDeviceId] semantics) don't require bumping config
 * schemaVersion and vice-versa.
 *
 * @property snapshotSchemaVersion Diagnostics only per `docs/architecture/wire-format.md` §3 —
 *   the reader gates on [minReaderVersion], never on this. The field keeps its distinct name
 *   because the envelope and the nested config version independently.
 * @property config Spec 008 `ConfigDocument` — carries own schemaVersion.
 * @property recordedAt Epoch millis. Server-side `serverTimestamp()`
 *   mapped on read.
 * @property recordedFromDeviceId Anti-spoof: server-enforced equal to
 *   `request.auth.uid` via Firestore Rule (FR-045a).
 */
// @EncodeDefault: history documents are encoded with `encodeDefaults = false`, and I1 requires the
// version fields on every document.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ConfigSnapshot(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("snapshotSchemaVersion")
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("snapshotMinReaderVersion")
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("snapshotMinWriterVersion")
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val config: ConfigDocument,
    val recordedAt: Long,
    val recordedFromDeviceId: String,
) : WireVersionHeader {
    companion object {
        /** What this build writes for the envelope. Was the integer 1 — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** The envelope is a thin wrapper; additions to it are ignorable by an older reader. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** History records are append-only and never rewritten, so no writer can degrade one. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}
