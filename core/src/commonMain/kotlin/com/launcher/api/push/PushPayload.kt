package com.launcher.api.push

import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

import kotlinx.serialization.json.JsonObject

/**
 * Domain shape of an FCM data-message after parsing
 * (contracts/fcm-payload.md). Adapters MUST translate raw
 * `Map<String, String>` from the FCM SDK into this type via
 * `FcmReceiverContract.parseFcmDataMap()` before invoking [PushReceiver].
 *
 *  - [extra]: type-specific payload, e.g. `{"cmdId": "..."}` for
 *    [PushType.CommandIssued]. `null` when not needed.
 *
 *  - Handlers MUST be idempotent — FCM may deliver duplicates
 *    (contract §Idempotency).
 */
data class PushPayload(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val type: PushType,
    val linkId: String,
    val extra: JsonObject? = null,
) : WireVersionHeader {
    companion object {
        /** What this build writes. Was the integer 1 before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** Push bodies are additive; unknown fields carry no meaning for an older reader. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** Write-once transport payload — never read-modify-written. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}
