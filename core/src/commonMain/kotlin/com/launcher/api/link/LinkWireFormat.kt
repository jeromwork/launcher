package com.launcher.api.link

import com.launcher.api.identity.AdminIdentity
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
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
    const val CURRENT_SCHEMA_VERSION: Int = 1

    fun parseSchemaVersionOnly(json: JsonElement): Int? {
        val obj = (json as? JsonObject) ?: return null
        return obj["schemaVersion"]?.jsonPrimitive?.intOrNull
    }

    data class Parsed(
        val schemaVersion: Int,
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
            put("schemaVersion", JsonPrimitive(CURRENT_SCHEMA_VERSION))
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

        val version = obj["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: return Outcome.Failure(BackendError.Unknown("link payload missing schemaVersion"))

        if (version > CURRENT_SCHEMA_VERSION) {
            return Outcome.Failure(BackendError.Unknown(
                "link schemaVersion=$version > supported $CURRENT_SCHEMA_VERSION — upgrade reader"
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
            adminId = AdminIdentity(adminUid),
            managedDeviceId = managedDeviceId,
            managedDeviceFirebaseUid = managedDeviceFirebaseUid,
            createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull,
            updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull,
        ))
    }
}
