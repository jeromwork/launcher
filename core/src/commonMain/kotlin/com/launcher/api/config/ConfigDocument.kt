package com.launcher.api.config

import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

import kotlinx.serialization.Serializable

/**
 * Wire-format root for `/links/{linkId}/config/current` (spec 008 §FR-001..006,
 * contracts/config.md).
 *
 * Schema invariants:
 *  - [schemaVersion] is read first by the deserializer (wire-format CHK002) и
 *    cannot decrease на update (Security Rules + FR-006).
 *  - [serverUpdatedAt] is server-set via `FieldValue.serverTimestamp()` — clients
 *    capture it as their snapshot для optimistic-concurrency precondition (FR-002,
 *    FR-012, FR-013).
 *  - [lastWriterDeviceId] identifies which editor (admin-phone / admin-tablet /
 *    Managed-phone) wrote the document last. Lives **only** в /config; **NOT
 *    mirrored** в /state to prevent voyeurism (security CHK019).
 *  - Each element ([flows], [slots] inside flows, [contacts]) has a UUID v4 [ElementId]
 *    so diff/merge can match по identity, не by position (FR-004).
 *
 * Backward-compat policy (FR-006): adding optional fields is additive (no version
 * bump); rename/remove requires bump → 2 + reader-migration в next spec.
 *
 * Forward-compat handling: spec 008 ships v=1 only; admin-v2 ↔ Managed-v1
 * mismatch handled by future spec `app-version-compatibility` (OUT-006).
 */
// @EncodeDefault: sync documents are encoded with `encodeDefaults = false`, and I1 requires the
// version fields on every document.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ConfigDocument(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val serverUpdatedAt: ServerTimestamp,
    val lastWriterDeviceId: String,
    val presetId: String,
    val flows: List<Flow>,
    val contacts: List<Contact>,
    /**
     * Spec 009 FR-013: forward-compat seam for per-link preset overrides.
     * Always null in spec 009 (additive change — no schemaVersion bump per
     * spec 008 FR-006). Spec 012 will populate.
     */
    val presetOverrides: PresetSettings? = null,
) : WireVersionHeader {
    companion object {
        /** What this build writes. Was the integer 1 before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** Optional additions (presetOverrides) are ignorable by an older reader per §3. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /**
         * Both admin and managed device edit this document (FR-050 «единый editor pattern»), so
         * an older writer really can round-trip a document authored by a newer one. Raise this
         * the moment a field appears that such a writer would drop, unless §6 unknown-field
         * preservation lands first.
         */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}
