package com.launcher.api.pairing

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Wire format for `/pairings/{token}` documents
 * (contracts/pairing-token.md v1).
 *
 * Field order on the wire matches the contract table; serialiser is hand-rolled
 * (not `@Serializable`) so the same module produces both the FCM-data flat-map
 * payload elsewhere AND the Firestore-friendly JsonObject here without
 * dragging two annotation surfaces.
 *
 * **Forward-compat policy** (CLAUDE.md §5):
 *  - Unknown fields → ignored (additive evolution is non-breaking).
 *  - `schemaVersion > CURRENT_SCHEMA_VERSION` → [deserialize] returns
 *    [BackendError.Unknown] so the reader logs and surfaces "update the app".
 *  - Renaming or removing a field requires bumping [CURRENT_SCHEMA_VERSION]
 *    and shipping a reader migration **before** the breaking write lands.
 */
object PairingWireFormat {
    const val CURRENT_SCHEMA_VERSION: Int = 1

    /** Cheap version probe — used by routers that decide which deserializer to invoke
     *  without paying for the full parse. Returns `null` if the field is missing or
     *  not an Int. */
    fun parseSchemaVersionOnly(json: JsonElement): Int? {
        val obj = (json as? JsonObject) ?: return null
        return obj["schemaVersion"]?.jsonPrimitive?.intOrNull
    }

    data class Parsed(
        val schemaVersion: Int,
        val pairingType: PairingType,
        val managedDeviceId: String,
        val managedDeviceFirebaseUid: String,
        val claimed: Boolean,
        val expiresAt: Long,
        val createdAt: Long?,
        val updatedAt: Long?,
        /** Set by admin during the claim transaction (FR-006). `null` before claim. */
        val linkId: String? = null,
        /** Set by admin during the claim transaction. Lets Managed render the
         *  consent screen (FR-007) without an extra `/links/{linkId}` read. */
        val adminId: String? = null,
    )

    fun serialize(
        token: PairingToken,
        managedDeviceId: String,
        managedDeviceFirebaseUid: String,
        expiresAt: Long,
        claimed: Boolean = false,
        pairingType: PairingType = PairingType.AdminManagedLink,
        createdAt: Long? = null,
        updatedAt: Long? = null,
        linkId: String? = null,
        adminId: String? = null,
    ): JsonObject {
        // token is encoded in the document id (path key), NOT in the body — kept
        // out of the body to avoid drift between key and field.
        val map = buildMap<String, JsonElement> {
            put("schemaVersion", JsonPrimitive(CURRENT_SCHEMA_VERSION))
            put("pairingType", JsonPrimitive(pairingType.wireValue))
            put("managedDeviceId", JsonPrimitive(managedDeviceId))
            put("managedDeviceFirebaseUid", JsonPrimitive(managedDeviceFirebaseUid))
            put("claimed", JsonPrimitive(claimed))
            put("expiresAt", JsonPrimitive(expiresAt))
            if (createdAt != null) put("createdAt", JsonPrimitive(createdAt))
            if (updatedAt != null) put("updatedAt", JsonPrimitive(updatedAt))
            if (linkId != null) put("linkId", JsonPrimitive(linkId))
            if (adminId != null) put("adminId", JsonPrimitive(adminId))
        }
        // suppress unused — kept signature symmetric for callers that pass token explicitly
        @Suppress("UNUSED_EXPRESSION") token
        return JsonObject(map)
    }

    fun deserialize(json: JsonElement): Outcome<Parsed, BackendError> {
        val obj = (json as? JsonObject)
            ?: return Outcome.Failure(BackendError.Unknown("pairing payload is not a JsonObject"))

        val version = obj["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: return Outcome.Failure(BackendError.Unknown("pairing payload missing schemaVersion"))

        // Future-version policy: a newer producer wrote a payload this reader
        // cannot safely interpret. Surface as Unknown so the UI can prompt
        // "update the app" rather than silently dropping fields. (T026)
        if (version > CURRENT_SCHEMA_VERSION) {
            return Outcome.Failure(BackendError.Unknown(
                "pairing schemaVersion=$version > supported $CURRENT_SCHEMA_VERSION — upgrade reader"
            ))
        }

        val pairingType = PairingType.fromWireOrNull(obj["pairingType"]?.jsonPrimitive?.contentOrNullSafe())
            ?: return Outcome.Failure(BackendError.Unknown("unknown pairingType"))

        val managedDeviceId = obj["managedDeviceId"]?.jsonPrimitive?.contentOrNullSafe()
            ?: return Outcome.Failure(BackendError.Unknown("missing managedDeviceId"))
        val managedDeviceFirebaseUid = obj["managedDeviceFirebaseUid"]?.jsonPrimitive?.contentOrNullSafe()
            ?: return Outcome.Failure(BackendError.Unknown("missing managedDeviceFirebaseUid"))
        val claimed = obj["claimed"]?.jsonPrimitive?.booleanOrNull
            ?: return Outcome.Failure(BackendError.Unknown("missing claimed"))
        val expiresAt = obj["expiresAt"]?.jsonPrimitive?.longOrNull
            ?: return Outcome.Failure(BackendError.Unknown("missing expiresAt"))

        return Outcome.Success(Parsed(
            schemaVersion = version,
            pairingType = pairingType,
            managedDeviceId = managedDeviceId,
            managedDeviceFirebaseUid = managedDeviceFirebaseUid,
            claimed = claimed,
            expiresAt = expiresAt,
            createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull,
            updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull,
            linkId = obj["linkId"]?.jsonPrimitive?.contentOrNullSafe(),
            adminId = obj["adminId"]?.jsonPrimitive?.contentOrNullSafe(),
        ))
    }

    @Suppress("unused")
    private val jsonInstance get() = WireFormatJson.json
}

private fun JsonPrimitive.contentOrNullSafe(): String? = if (this.isString) this.content else null
