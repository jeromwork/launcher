package com.launcher.api.link

import family.wire.WireVersion

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Wire format for `/links/{linkId}/state/current` bootstrap snapshots
 * (contracts/state-bootstrap.md v1).
 *
 * **Spec 008 will extend this** with flows/slots/appliedCapabilities as
 * additive fields. [LinkBootstrap.SCHEMA_VERSION] stays `"1.0"` until a rename or
 * removal is required (see contract §Backward compatibility).
 */
object LinkBootstrapWireFormat {
    /** This reader's level for the gate of `docs/architecture/wire-format.md` §3. */
    val READER_LEVEL: WireVersion = LinkBootstrap.SCHEMA_VERSION

    /** Diagnostics only (§3) — no reader decision may depend on this. */
    fun parseSchemaVersionOnly(json: JsonElement): WireVersion? {
        val obj = (json as? JsonObject) ?: return null
        return obj["schemaVersion"]?.jsonPrimitive?.contentOrNull?.let { WireVersion.parseOrNull(it) }
    }

    fun serialize(bootstrap: LinkBootstrap): JsonObject {
        val map = buildMap<String, JsonElement> {
            put("schemaVersion", JsonPrimitive(LinkBootstrap.SCHEMA_VERSION.toString()))
        put("minReaderVersion", JsonPrimitive(LinkBootstrap.MIN_READER_VERSION.toString()))
        put("minWriterVersion", JsonPrimitive(LinkBootstrap.MIN_WRITER_VERSION.toString()))
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

        // Header first (§4), gate on minReaderVersion rather than schemaVersion (§3).
        val version = obj["schemaVersion"]?.jsonPrimitive?.contentOrNull?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(BackendError.Unknown("state-bootstrap missing/unreadable schemaVersion"))
        val minReader = obj["minReaderVersion"]?.jsonPrimitive?.contentOrNull?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(BackendError.Unknown("state-bootstrap missing/unreadable minReaderVersion"))
        val minWriter = obj["minWriterVersion"]?.jsonPrimitive?.contentOrNull?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(BackendError.Unknown("state-bootstrap missing/unreadable minWriterVersion"))

        if (READER_LEVEL < minReader) {
            return Outcome.Failure(BackendError.Unknown(
                "state-bootstrap requires a reader at $minReader; this build is $READER_LEVEL — upgrade reader"
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
