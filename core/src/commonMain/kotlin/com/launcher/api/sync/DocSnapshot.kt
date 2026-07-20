package com.launcher.api.sync

import family.wire.WireVersion

import kotlinx.serialization.json.JsonElement

/**
 * Backend-agnostic document snapshot. `data` is opaque [JsonElement] (not
 * `Map<String, Any?>`) so adapters never leak vendor types into the domain
 * (spec 007 §FR-013, CLAUDE.md §2).
 *
 *  - [schemaVersion]: parsed from `data` by the adapter; mirrored here for fast
 *    routing without re-parsing the payload.
 *  - [updatedAt]: epoch millis; `null` until the server-side timestamp lands
 *    (e.g. local-only write that has not yet round-tripped).
 *
 *    TODO(upgrade): when the project adopts `kotlinx-datetime`, swap [Long]
 *    epoch-millis for `kotlinx.datetime.Instant`. Held as Long today to match
 *    existing convention in `Health.lastSeen` and avoid adding a dep for one
 *    spec (CLAUDE.md §4 — MVA).
 *
 *  - [isStale]: `true` when the source is an offline cache (see
 *    `FakeRemoteSyncBackend` queue/isStale per spec 007 C5).
 */
data class DocSnapshot(
    val path: DocPath,
    val data: JsonElement,
    /** Diagnostics only (§3); null when the document predates the dotted-version conversion. */
    val schemaVersion: WireVersion?,
    val updatedAt: Long?,
    val isStale: Boolean = false,
)
