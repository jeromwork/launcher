package family.push.api

import family.wire.WireVersion

/**
 * T010 — Single source of truth для maximum supported wire-format schemaVersion
 * на client. Per spec 019 FR-013, data-model.md §WireFormatVersion.
 *
 * Mirror в TypeScript: `workers/push/src/contract/wire-format.ts`
 * `MAX_SUPPORTED_SCHEMA_VERSION` const + Worker var `MAX_SUPPORTED_SCHEMA_VERSION`.
 * T402 fitness function проверяет что обе константы equal.
 *
 * Asymmetric policy (forward-compat at receiver), expressed via the reader gate of
 * `docs/architecture/wire-format.md` §3 — the receiver drops only what asks for a newer reader:
 *  • Client outbound: encodes `schemaVersion = MAX_SUPPORTED_SCHEMA_VERSION` (current).
 *  • Client inbound: if incoming `schemaVersion > MAX_SUPPORTED` → silent log + ignore
 *    (fail-soft, per spec.md §Wire-format policy).
 *  • Worker inbound: if incoming `schemaVersion > MAX_SUPPORTED` → 400 (fail-fast, prevent
 *    forge-через-future-schema attacks).
 */
object WireFormatVersion {
    /** What this build writes. Was the integer 1 before the conversion — never lowered (I3). */
    val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

    /** Push bodies are additive; an older receiver ignores fields it does not know (§3). */
    val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

    /** Write-once transport payload — never read-modify-written. */
    val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
}
