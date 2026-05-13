package com.launcher.api.link

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Wire format for `/links/{linkId}/state/current` bootstrap snapshots
 * (contracts/state-bootstrap.md v1).
 *
 * **Spec 008 will extend this** with flows/slots/appliedCapabilities as
 * additive fields. [CURRENT_SCHEMA_VERSION] stays `1` until a rename or
 * removal is required (see contract §Backward compatibility).
 */
object LinkBootstrapWireFormat {
    const val CURRENT_SCHEMA_VERSION: Int = LinkBootstrap.SCHEMA_VERSION

    fun parseSchemaVersionOnly(json: JsonElement): Int? {
        val obj = (json as? JsonObject) ?: return null
        return obj["schemaVersion"]?.jsonPrimitive?.intOrNull
    }

    fun serialize(bootstrap: LinkBootstrap): JsonObject {
        val map = buildMap<String, JsonElement> {
            put("schemaVersion", JsonPrimitive(CURRENT_SCHEMA_VERSION))
            put("appliedAt", JsonPrimitive(bootstrap.appliedAt))
            put("presetId", JsonPrimitive(bootstrap.presetId))
            if (bootstrap.fcmToken != null) {
                put("fcmToken", JsonPrimitive(bootstrap.fcmToken))
            }
        }
        return JsonObject(map)
    }

    fun deserialize(json: JsonElement): Outcome<LinkBootstrap, BackendError> {
        val obj = (json as? JsonObject)
            ?: return Outcome.Failure(BackendError.Unknown("state-bootstrap payload is not a JsonObject"))

        val version = obj["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: return Outcome.Failure(BackendError.Unknown("state-bootstrap missing schemaVersion"))

        if (version > CURRENT_SCHEMA_VERSION) {
            return Outcome.Failure(BackendError.Unknown(
                "state-bootstrap schemaVersion=$version > supported $CURRENT_SCHEMA_VERSION — upgrade reader"
            ))
        }

        val appliedAt = obj["appliedAt"]?.jsonPrimitive?.longOrNull
            ?: return Outcome.Failure(BackendError.Unknown("missing appliedAt"))
        val presetId = obj["presetId"]?.jsonPrimitive?.let { if (it.isString) it.content else null }
            ?: return Outcome.Failure(BackendError.Unknown("missing presetId"))

        // fcmToken is nullable on the wire — absence means GMS-less device (C13).
        val fcmToken = obj["fcmToken"]?.jsonPrimitive?.let { if (it.isString) it.content else null }

        return Outcome.Success(LinkBootstrap(
            schemaVersion = version,
            appliedAt = appliedAt,
            presetId = presetId,
            fcmToken = fcmToken,
        ))
    }
}
