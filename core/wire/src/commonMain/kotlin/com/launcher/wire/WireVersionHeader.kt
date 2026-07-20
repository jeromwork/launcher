package com.launcher.wire

/**
 * The three version fields every wire format carries from its first commit — invariant I1 of
 * `docs/architecture/wire-format.md`.
 *
 * A version is a runtime instrument, not a label: the document states the minimum reader it
 * needs and the minimum writer it needs, so a reader gets **three** outcomes instead of two.
 * Model: Matroska `DocTypeReadVersion` (RFC 9559) and SQLite's read/write version bytes.
 */
interface WireVersionHeader {
    /** What wrote this. Diagnostics only — no reader decision may depend on it (§3). */
    val schemaVersion: WireVersion

    /** Minimum level required to interpret correctly. A reader below it MUST refuse (§3). */
    val minReaderVersion: WireVersion

    /** Minimum level required to write back without destroying meaning; below it → read-only (§3). */
    val minWriterVersion: WireVersion
}

/** The three outcomes of the reader gate — `docs/architecture/wire-format.md` §3. */
enum class WireAccess {
    /** Reader understands the document and may write it back. */
    FULL,

    /**
     * Reader understands the document but would destroy meaning by writing it back. Surface this
     * to the user ("configured in a newer version — update to change"); never write and silently
     * degrade.
     */
    READ_ONLY,

    /** Reader cannot interpret the document. Reached only via [UnknownWireVersionException]. */
    REFUSED,
}

/**
 * Applies the reader gate of §3 and returns which of the three outcomes this reader gets.
 *
 * @param readerLevel the version this build of the reader implements — for a given format, its
 *   `SCHEMA_VERSION` constant (§11: one named constant per format, declared beside the type).
 * @throws UnknownWireVersionException when the document requires a newer reader (§4, fail closed).
 * @throws CorruptWireFormatException when the header violates
 *   `minReaderVersion <= minWriterVersion <= schemaVersion` (§8, fail loudly).
 */
fun WireVersionHeader.accessFor(readerLevel: WireVersion): WireAccess {
    if (minReaderVersion > minWriterVersion || minWriterVersion > schemaVersion) {
        throw CorruptWireFormatException(
            "Version header out of order: minReaderVersion=$minReaderVersion, " +
                "minWriterVersion=$minWriterVersion, schemaVersion=$schemaVersion — " +
                "expected minReaderVersion <= minWriterVersion <= schemaVersion."
        )
    }
    if (readerLevel < minReaderVersion) {
        throw UnknownWireVersionException(
            "Document requires a reader at $minReaderVersion or above; this reader is " +
                "$readerLevel. Refusing rather than guessing the shape."
        )
    }
    return if (readerLevel < minWriterVersion) WireAccess.READ_ONLY else WireAccess.FULL
}
