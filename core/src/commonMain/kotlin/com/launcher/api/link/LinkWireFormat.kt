package com.launcher.api.link

import com.launcher.wire.WireVersion

import com.launcher.api.identity.AdminIdentity
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Wire format for `/links/{linkId}` root documents (contracts/link.md v1).
 *
 * The `linkId` is the Firestore document key, NOT part of the body —
 * callers pass it separately on serialize and read it from
 * `DocPath.Links(linkId)` on deserialize.
 *
 * Forward-compat policy: same as [com.launcher.api.pairing.PairingWireFormat].
 */
object LinkWireFormat {
    /** What this build writes. Was the integer 1 before the conversion — never lowered (I3). */
    val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

    /** Fields are additive; an older reader safely ignores what it does not know (§3). */
    val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

    /** Write-once payload — never read-modify-written by a second party. */
    val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)

    /** Diagnostics only (§3) — no reader decision may depend on this. */
    fun parseSchemaVersionOnly(json: JsonElement): WireVersion? {
        val obj = (json as? JsonObject) ?: return null
        return obj["schemaVersion"]?.jsonPrimitive?.contentOrNull?.let { WireVersion.parseOrNull(it) }
    }

    data class Parsed(
        val schemaVersion: WireVersion,
        val minReaderVersion: WireVersion,
        val minWriterVersion: WireVersion,
        val adminId: AdminIdentity,
        val managedDeviceId: String,
        val managedDeviceFirebaseUid: String,
        val createdAt: Long?,
        val updatedAt: Long?,
    )

    fun serialize(
        adminId: AdminIdentity,
        managedDeviceId: String,
        managedDeviceFirebaseUid: String,
        createdAt: Long? = null,
        updatedAt: Long? = null,
    ): JsonObject {
        val map = buildMap<String, JsonElement> {
            put("schemaVersion", JsonPrimitive(SCHEMA_VERSION.toString()))
        put("minReaderVersion", JsonPrimitive(MIN_READER_VERSION.toString()))
        put("minWriterVersion", JsonPrimitive(MIN_WRITER_VERSION.toString()))
            put("adminId", JsonPrimitive(adminId.firebaseAuthUid))
            put("managedDeviceId", JsonPrimitive(managedDeviceId))
            put("managedDeviceFirebaseUid", JsonPrimitive(managedDeviceFirebaseUid))
            if (createdAt != null) put("createdAt", JsonPrimitive(createdAt))
            if (updatedAt != null) put("updatedAt", JsonPrimitive(updatedAt))
        }
        return JsonObject(map)
    }

    fun deserialize(json: JsonElement): Outcome<Parsed, BackendError> {
        val obj = (json as? JsonObject)
            ?: return Outcome.Failure(BackendError.Unknown("link payload is not a JsonObject"))

        // Read the header first (§4), then gate on minReaderVersion rather than schemaVersion:
        // a payload from a newer writer whose additions we can ignore must stay readable (§3).
        val version = obj["schemaVersion"]?.jsonPrimitive?.contentOrNull?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(BackendError.Unknown("link payload missing/unreadable schemaVersion"))
        val minReader = obj["minReaderVersion"]?.jsonPrimitive?.contentOrNull?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(BackendError.Unknown("link payload missing/unreadable minReaderVersion"))
        val minWriter = obj["minWriterVersion"]?.jsonPrimitive?.contentOrNull?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(BackendError.Unknown("link payload missing/unreadable minWriterVersion"))

        if (SCHEMA_VERSION < minReader) {
            return Outcome.Failure(BackendError.Unknown(
                "link payload requires a reader at $minReader; this build is $SCHEMA_VERSION — upgrade reader"
            ))
        }

        val adminUid = obj["adminId"]?.jsonPrimitive?.let { if (it.isString) it.content else null }
            ?: return Outcome.Failure(BackendError.Unknown("missing adminId"))
        val managedDeviceId = obj["managedDeviceId"]?.jsonPrimitive?.let { if (it.isString) it.content else null }
            ?: return Outcome.Failure(BackendError.Unknown("missing managedDeviceId"))
        val managedDeviceFirebaseUid = obj["managedDeviceFirebaseUid"]?.jsonPrimitive?.let { if (it.isString) it.content else null }
            ?: return Outcome.Failure(BackendError.Unknown("missing managedDeviceFirebaseUid"))

        return Outcome.Success(Parsed(
            schemaVersion = version,
            minReaderVersion = minReader,
            minWriterVersion = minWriter,
            adminId = AdminIdentity(adminUid),
            managedDeviceId = managedDeviceId,
            managedDeviceFirebaseUid = managedDeviceFirebaseUid,
            createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull,
            updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull,
        ))
    }
}
