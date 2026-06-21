package family.push.internal

import family.push.api.WireFormatVersion
import kotlinx.serialization.Serializable

/**
 * T015 — HTTP body для POST /push к Worker. Per data-model.md §PushTriggerRequest,
 * contracts/push-trigger-request-v1.md.
 *
 * `internal` — wire DTO не expose'ится в public API. Caller использует
 * [family.push.api.PushTrigger] interface (порт) — see DefaultPushTrigger orchestrator.
 *
 * Mirror в TypeScript: `workers/push/src/contract/wire-format.ts`
 * `interface PushTriggerRequest`. Любые расхождения = silent prod failure
 * (R10). Mitigation: T402 fitness function (CI script) + integration test
 * (T082, Worker integration.test.ts).
 */
@Serializable
internal data class PushTriggerRequest(
    val schemaVersion: Int = WireFormatVersion.MAX_SUPPORTED_SCHEMA_VERSION,
    val eventType: String,
    val targetScope: String,
    val ownerUid: String,
    val payload: Map<String, String> = emptyMap(),
)
