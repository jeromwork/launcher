package com.launcher.api.config

import kotlinx.serialization.Serializable

/**
 * Domain-typed wrapper around Firestore's server-set timestamp (`FieldValue.serverTimestamp()`).
 *
 * Used as the **version cookie** for optimistic concurrency на `/config/current`
 * (spec 008 §FR-002, FR-012, FR-013). Client reads the value, sends it back с
 * write attempt, server-side transaction aborts if mismatch.
 *
 * Per research.md §1: chosen over an `Int optimisticVersion` counter because
 * (a) server-authored (immune to client clock skew); (b) Firestore-native via
 * `FieldValue.serverTimestamp()`; (c) monotonic per-document.
 *
 * The Android adapter (`FirebaseRemoteSyncBackend` from spec 007) maps
 * `com.google.firebase.Timestamp` ↔ this type — vendor type never crosses
 * commonMain boundary.
 *
 * **NOT** the same as `appliedAt`/`updatedAt` (which are epoch-millis from spec 007).
 * `ServerTimestamp` keeps nanosecond precision because comparisons happen в
 * tight time windows (concurrent edits within seconds).
 */
@Serializable
data class ServerTimestamp(
    val epochSeconds: Long,
    val nanoseconds: Int,
) : Comparable<ServerTimestamp> {

    override fun compareTo(other: ServerTimestamp): Int {
        val s = epochSeconds.compareTo(other.epochSeconds)
        return if (s != 0) s else nanoseconds.compareTo(other.nanoseconds)
    }

    companion object {
        /** Sentinel for «never written» state (always < any real timestamp). */
        val Never: ServerTimestamp = ServerTimestamp(epochSeconds = 0L, nanoseconds = 0)
    }
}
