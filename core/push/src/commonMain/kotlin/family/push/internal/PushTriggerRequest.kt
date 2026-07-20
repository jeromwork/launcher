package family.push.internal

import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

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
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PushTriggerRequest(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = WireFormatVersion.SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = WireFormatVersion.MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = WireFormatVersion.MIN_WRITER_VERSION,
    val eventType: String,
    val targetScope: String,
    val ownerUid: String,
    val payload: Map<String, String> = emptyMap(),
) : WireVersionHeader
