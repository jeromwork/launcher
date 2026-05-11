package com.launcher.api.push

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Wire format for the FCM data-message (contracts/fcm-payload.md v1).
 *
 * FCM `data` payloads are **flat `Map<String, String>`** (FCM API constraint
 * — all values are strings, even integers). So this format encodes/decodes
 * via `Map<String, String>` rather than `JsonElement`.
 *
 * Forward-compat policy:
 *  - Unknown fields → ignored (additive).
 *  - Unknown `type` value → [parse] returns `null` so the receiver **drops**
 *    the push gracefully (old Managed app receiving a new push type).
 *  - `schemaVersion > CURRENT_SCHEMA_VERSION` → drop (return null). FCM
 *    re-delivery is not useful here — server-side bump would have to wait
 *    for client roll-out.
 */
object PushPayloadWireFormat {
    const val CURRENT_SCHEMA_VERSION: Int = PushPayload.SCHEMA_VERSION

    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_TYPE = "type"
    private const val KEY_LINK_ID = "linkId"
    private const val KEY_CMD_ID = "cmdId"

    /** Cheap version probe over the flat data-map. Returns `null` if the field
     *  is missing or not parseable as Int. */
    fun parseSchemaVersionOnly(data: Map<String, String>): Int? =
        data[KEY_SCHEMA_VERSION]?.toIntOrNull()

    /** Build the data-map sent by Worker → FCM. Stringifies all values. */
    fun encode(payload: PushPayload): Map<String, String> {
        val map = buildMap {
            put(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION.toString())
            put(KEY_TYPE, payload.type.wireValue)
            put(KEY_LINK_ID, payload.linkId)
            // Promote type-specific extras to top-level (FCM data is flat).
            // The contract today knows only cmdId; future extras follow the
            // same pattern. See FcmReceiverContract.parseFcmDataMap.
            payload.extra?.get(KEY_CMD_ID)?.let { el ->
                val prim = (el as? JsonPrimitive) ?: return@let
                if (prim.isString) put(KEY_CMD_ID, prim.content)
            }
        }
        return map
    }

    /** Parse the flat data-map back into a domain [PushPayload].
     *  Returns `null` if the message is malformed or unsupported — the
     *  receiver MUST drop silently (FCM may redeliver an old/new type). */
    fun parse(data: Map<String, String>): PushPayload? {
        val version = data[KEY_SCHEMA_VERSION]?.toIntOrNull() ?: return null
        // Future-version policy: a newer producer wrote a payload this reader
        // cannot interpret. Drop silently — no UI surface since silent push. (T026)
        if (version > CURRENT_SCHEMA_VERSION) return null

        val type = PushType.fromWireOrNull(data[KEY_TYPE]) ?: return null
        val linkId = data[KEY_LINK_ID] ?: return null

        val extra = data[KEY_CMD_ID]?.let { cmdId ->
            buildJsonObject { put(KEY_CMD_ID, JsonPrimitive(cmdId)) }
        }

        return PushPayload(
            schemaVersion = version,
            type = type,
            linkId = linkId,
            extra = extra,
        )
    }

    /** Worker-side serialization to JsonObject for the FCM HTTP v1 `data` field.
     *  Wraps [encode] in a [JsonObject] of strings. */
    fun encodeAsJsonObject(payload: PushPayload): JsonObject {
        val map = encode(payload).mapValues { JsonPrimitive(it.value) }
        return JsonObject(map)
    }

    /** Convenience: roundtrip via Outcome for symmetry with other wire formats.
     *  Maps `null` parse result to [BackendError.Unknown] so callers that want
     *  the typed-error channel get it. */
    fun deserialize(data: Map<String, String>): Outcome<PushPayload, BackendError> =
        parse(data)?.let { Outcome.Success(it) }
            ?: Outcome.Failure(BackendError.Unknown("push payload malformed or unsupported"))
}
