package family.push.api

/**
 * T010 — Single source of truth для maximum supported wire-format schemaVersion
 * на client. Per spec 019 FR-013, data-model.md §WireFormatVersion.
 *
 * Mirror в TypeScript: `workers/push/src/contract/wire-format.ts`
 * `MAX_SUPPORTED_SCHEMA_VERSION` const + Worker var `MAX_SUPPORTED_SCHEMA_VERSION`.
 * T402 fitness function проверяет что обе константы equal.
 *
 * Asymmetric policy (forward-compat at receiver):
 *  • Client outbound: encodes `schemaVersion = MAX_SUPPORTED_SCHEMA_VERSION` (current).
 *  • Client inbound: if incoming `schemaVersion > MAX_SUPPORTED` → silent log + ignore
 *    (fail-soft, per spec.md §Wire-format policy).
 *  • Worker inbound: if incoming `schemaVersion > MAX_SUPPORTED` → 400 (fail-fast, prevent
 *    forge-через-future-schema attacks).
 */
object WireFormatVersion {
    const val MAX_SUPPORTED_SCHEMA_VERSION: Int = 1
}
