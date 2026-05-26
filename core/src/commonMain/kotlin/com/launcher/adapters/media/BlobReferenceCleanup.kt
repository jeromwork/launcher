package com.launcher.adapters.media

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Spec 012 — narrow port для refCount decrement. Pair to [BlobReferenceWriter].
 *
 * Used by:
 *  - Contact deletion flow (FR-013 implicit overwrite + FR-023 manual delete).
 *  - Slot(Document) deletion flow (FR-023).
 *
 * Implementation lives в androidMain через wrapper над
 * `SqlDelightBlobReferenceLedger.removeRef` (existing 011 query).
 *
 * Task: T1249 (Phase 7). FR-013, FR-023.
 */
@OptIn(ExperimentalUuidApi::class)
fun interface BlobReferenceRemover {
    suspend fun removeRef(uuid: Uuid, linkId: String, refSource: String)
}

/**
 * Convenience: compute refSource string для config-current references.
 * Format documented в `BlobReferenceLedger.sq` enum-like values:
 *   - "config-current:contact:<contactId>"
 *   - "config-current:slot:<slotId>"
 *
 * Task: T1249.
 */
@OptIn(ExperimentalUuidApi::class)
object RefSource {
    fun forContactPhoto(contactId: String): String = "config-current:contact:$contactId"
    fun forDocumentSlot(slotId: String): String = "config-current:slot:$slotId"

    /**
     * Parse iconRef "private:<uuid>" → Uuid. Returns null если invalid.
     * Used by deletion flow to know which blob's refCount к decrement'у.
     */
    fun parseIconRefUuid(iconRef: String?): Uuid? {
        if (iconRef == null || !iconRef.startsWith("private:")) return null
        return try {
            Uuid.parse(iconRef.removePrefix("private:"))
        } catch (_: Throwable) {
            null
        }
    }
}
