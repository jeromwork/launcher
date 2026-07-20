package com.launcher.adapters.auth

import family.wire.WireVersion

/**
 * Version headers for the two documents written during sign-in — `identity-links/{linkId}` and
 * `users/{stableId}` (spec 017 FR-016a, contract `identity-link-v1.md`).
 *
 * These two have no Kotlin type: they are assembled as ad-hoc Firestore maps in
 * [GoogleSignInAuthAdapter], because they carry three fields and are never read back into a
 * domain object. That is why they were invisible when every typed format moved to the dotted
 * version (TASK-138) — nothing failed to compile, while `firestore.rules` had already been
 * switched to require a string header, so creation was rejected at write time.
 *
 * `docs/architecture/wire-format.md` §11 asks for one named constant per format declared beside
 * the type. With no type to sit beside, they live here — and the version stops being a literal
 * buried in a map builder.
 *
 * Deliberately in the adapter source set, not in the domain: these are Firestore document
 * shapes, and the identifiers in them (`stableId`) exist to keep the provider's account id out
 * of every other document (rule 13, opaque identifiers).
 */
internal object IdentityDocumentWireFormat {

    /** `identity-links/{provider}_{accountId}` — reverse mapping to the opaque stable id. */
    object IdentityLink {
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }

    /** `users/{stableId}` — the account root document. */
    object UserRoot {
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }

    /**
     * The three header entries, ready to splat into a Firestore map.
     *
     * Written as strings because that is what the wire carries and what
     * `hasValidVersionHeader()` in `firestore.rules` checks — the rules compare through
     * `versionOrder()`, which parses the dotted form; a number there fails the type test and the
     * whole write is denied.
     */
    fun header(
        schemaVersion: WireVersion,
        minReaderVersion: WireVersion,
        minWriterVersion: WireVersion,
    ): Map<String, Any> = mapOf(
        "schemaVersion" to schemaVersion.toString(),
        "minReaderVersion" to minReaderVersion.toString(),
        "minWriterVersion" to minWriterVersion.toString(),
    )
}
