package com.launcher.api.contacts

import com.launcher.api.result.Outcome

/**
 * Parses RFC 6350 vCard 3.0/4.0 payloads (spec 009 FR-028).
 * Adapter — **hand-written ~100 LOC parser, FN + TEL only** — lives in
 * `androidMain`. NOT `ezvcard` library (would leak vendor types per
 * CLAUDE.md rule 1). Domain sees only [RawVCard].
 *
 * Whitelist policy (FR-028): only FN/N and TEL fields are extracted;
 * everything else is ignored. Payload cap 10 KB enforced by adapter to
 * bound parse time (NFR-002 p95 < 100 ms target).
 */
interface VCardImporter {

    suspend fun parse(payload: ByteArray): Outcome<RawVCard, ImportError>
}

data class RawVCard(
    val displayName: String,
    val phoneNumbers: List<String>,
)

sealed interface ImportError {
    data class PayloadTooLarge(val sizeBytes: Long, val maxBytes: Long) : ImportError
    data object NonUtf8 : ImportError
    data object MissingFn : ImportError       // no FN/N field
    data object MissingTel : ImportError      // no TEL field
    data class MalformedVCard(val reason: String) : ImportError
}
