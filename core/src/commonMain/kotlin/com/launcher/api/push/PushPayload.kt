package com.launcher.api.push

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
    val schemaVersion: Int = SCHEMA_VERSION,
    val type: PushType,
    val linkId: String,
    val extra: JsonObject? = null,
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}
