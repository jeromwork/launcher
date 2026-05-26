package com.launcher.api.contacts

import com.launcher.api.result.Outcome

/**
 * Parses RFC 6350 vCard 3.0/4.0 payloads (spec 009 FR-028).
 * Adapter — **hand-written ~100 LOC parser, FN + TEL only** — lives in
 * `androidMain`. NOT `ezvcard` library (would leak vendor types per
 * CLAUDE.md rule 1). Domain sees only [RawVCard].
 *
 * Whitelist policy (FR-028 + spec 012 FR-011): FN/N, TEL, and PHOTO fields
 * extracted; everything else is ignored. Payload cap 10 KB enforced by
 * adapter to bound parse time (NFR-002 p95 < 100 ms target).
 *
 * Spec 012 extension: PHOTO field (RFC 6350 §6.2.4, "PHOTO") when ENCODING=b
 * (base64). If present, photo bytes делают доступными для admin-side upload
 * через PrivateMediaUploader.
 */
interface VCardImporter {

    suspend fun parse(payload: ByteArray): Outcome<RawVCard, ImportError>
}

data class RawVCard(
    val displayName: String,
    val phoneNumbers: List<String>,
    /**
     * Spec 012 FR-011 — extracted PHOTO field bytes (base64-decoded).
     *
     * `null` when:
     *  - vCard payload has no PHOTO line, OR
     *  - PHOTO encoding is not base64 (URI form unsupported in 012), OR
     *  - PHOTO bytes failed base64 decode.
     *
     * Caller (admin contact-add flow) feeds these bytes into
     * `PrivateMediaUploader.upload(kind = Image)` if non-null.
     */
    val photoBytes: ByteArray? = null,
) {
    // ByteArray equals/hashCode требует ручной реализации.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawVCard) return false
        if (displayName != other.displayName) return false
        if (phoneNumbers != other.phoneNumbers) return false
        if (photoBytes == null && other.photoBytes == null) return true
        if (photoBytes == null || other.photoBytes == null) return false
        return photoBytes.contentEquals(other.photoBytes)
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + phoneNumbers.hashCode()
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        return result
    }
}

sealed interface ImportError {
    data class PayloadTooLarge(val sizeBytes: Long, val maxBytes: Long) : ImportError
    data object NonUtf8 : ImportError
    data object MissingFn : ImportError       // no FN/N field
    data object MissingTel : ImportError      // no TEL field
    data class MalformedVCard(val reason: String) : ImportError
}
