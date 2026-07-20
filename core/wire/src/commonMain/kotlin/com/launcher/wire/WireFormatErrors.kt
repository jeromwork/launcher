package com.launcher.wire

/**
 * A document declares a version this reader cannot interpret, or the version string itself is
 * malformed — `docs/architecture/wire-format.md` §4. The reader MUST stop here: never guess the
 * shape, never best-effort parse, never decrypt to find out.
 *
 * Callers MUST be able to tell this apart from [CorruptWireFormatException] (§8): unknown means
 * *we* are too old and the fix is to update; corrupt means the document is broken and updating
 * will not help. Different UI, different fix.
 */
class UnknownWireVersionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The document is structurally broken — a dangling reference, a negative length, or a version
 * header violating `minReaderVersion <= minWriterVersion <= schemaVersion`
 * (`docs/architecture/wire-format.md` §8).
 *
 * Fail loudly. Never repair on the fly: RFC 9413 §4.1 — on-the-fly repair entrenches the other
 * side's bug, forces every other implementation to replicate it, and the flaw becomes a de-facto
 * standard nobody can remove.
 */
class CorruptWireFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
