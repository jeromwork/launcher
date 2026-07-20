package com.launcher.api.config

import com.launcher.wire.WireVersion

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Wire format для `/links/{linkId}/config/current` (contracts/config.md v1).
 *
 * Hand-rolled serialization (mirroring LinkBootstrapWireFormat pattern) to
 * exercise tight control over wire invariants:
 *  - **schemaVersion read FIRST** (wire-format CHK002);
 *  - unknown slot.kind → fail-closed (CHK009);
 *  - missing optional fields filled with defaults per FR-006 additive policy;
 *  - extra unknown fields ignored (forward-compat).
 *
 * Spec 008 ships v=1 only. Future v=2 will require a routing reader (see
 * FR-006) — for now the parseSchemaVersionOnly helper supports that future.
 */
object ConfigDocumentWireFormat {
    /** This reader's level for the gate of `docs/architecture/wire-format.md` §3. */
    val READER_LEVEL: WireVersion = ConfigDocument.SCHEMA_VERSION

    /** Diagnostics only (§3) — no reader decision may depend on this. */
    fun parseSchemaVersionOnly(json: JsonElement): WireVersion? {
        val obj = json as? JsonObject ?: return null
        return obj["schemaVersion"]?.jsonPrimitive?.contentOrNull?.let { WireVersion.parseOrNull(it) }
    }

    fun serialize(config: ConfigDocument): JsonObject = buildJsonObject {
        put("schemaVersion", config.schemaVersion.toString())
        put("minReaderVersion", config.minReaderVersion.toString())
        put("minWriterVersion", config.minWriterVersion.toString())
        put("serverUpdatedAt", buildJsonObject {
            put("epochSeconds", config.serverUpdatedAt.epochSeconds)
            put("nanoseconds", config.serverUpdatedAt.nanoseconds)
        })
        put("lastWriterDeviceId", config.lastWriterDeviceId)
        put("presetId", config.presetId)
        put("flows", JsonArray(config.flows.map { serializeFlow(it) }))
        put("contacts", JsonArray(config.contacts.map { serializeContact(it) }))
        // Spec 009 FR-013 — additive field. Omit when null so spec-008
        // readers see exactly the same wire as before (CHK forward-compat).
        if (config.presetOverrides != null) {
            put("presetOverrides", serializePresetSettings(config.presetOverrides))
        }
    }

    fun deserialize(json: JsonElement): Outcome<ConfigDocument, BackendError> {
        val obj = json as? JsonObject
            ?: return Outcome.Failure(BackendError.Unknown("config payload is not a JsonObject"))

        // Read the version header FIRST (CHK002, wire-format.md §4) and gate on minReaderVersion,
        // not schemaVersion: a document written by a newer build whose additions we can ignore
        // must still be readable (§3). Only a raised reader minimum refuses.
        val minReader = obj["minReaderVersion"]?.jsonPrimitive?.contentOrNull
            ?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(BackendError.Unknown("config missing/unreadable minReaderVersion"))

        if (READER_LEVEL < minReader) {
            return Outcome.Failure(
                BackendError.Unknown(
                    "config requires a reader at $minReader; this build is $READER_LEVEL — " +
                        "upgrade reader (see OUT-006 app-version-compatibility)"
                )
            )
        }

        val schemaVersion = obj["schemaVersion"]?.jsonPrimitive?.contentOrNull
            ?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(BackendError.Unknown("config missing/unreadable schemaVersion"))
        val minWriter = obj["minWriterVersion"]?.jsonPrimitive?.contentOrNull
            ?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(BackendError.Unknown("config missing/unreadable minWriterVersion"))

        val serverUpdatedAt = parseServerTimestamp(obj["serverUpdatedAt"])
            ?: return Outcome.Failure(BackendError.Unknown("config missing/invalid serverUpdatedAt"))

        val lastWriterDeviceId = obj["lastWriterDeviceId"]?.jsonPrimitive
            ?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("config missing lastWriterDeviceId"))

        val presetId = obj["presetId"]?.jsonPrimitive
            ?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("config missing presetId"))

        // Optional arrays default to empty per FR-006 (additive: pre-v1 docs
        // without these fields read OK).
        val flowsArr = (obj["flows"] as? JsonArray) ?: JsonArray(emptyList())
        val contactsArr = (obj["contacts"] as? JsonArray) ?: JsonArray(emptyList())

        val flows = mutableListOf<Flow>()
        for (e in flowsArr) {
            when (val r = deserializeFlow(e)) {
                is Outcome.Success -> flows.add(r.value)
                is Outcome.Failure -> return r // fail-closed
            }
        }

        val contacts = mutableListOf<Contact>()
        for (e in contactsArr) {
            when (val r = deserializeContact(e)) {
                is Outcome.Success -> contacts.add(r.value)
                is Outcome.Failure -> return r
            }
        }

        // Spec 009 FR-013 — additive field. Absence ⇒ null (default).
        // Unknown content inside presetOverrides is itself additive
        // per spec 008 FR-006 (fields unknown to this reader are skipped).
        val presetOverrides = (obj["presetOverrides"] as? JsonObject)?.let { ps ->
            when (val r = deserializePresetSettings(ps)) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> return r
            }
        }

        return Outcome.Success(
            ConfigDocument(
                schemaVersion = schemaVersion,
                minReaderVersion = minReader,
                minWriterVersion = minWriter,
                serverUpdatedAt = serverUpdatedAt,
                lastWriterDeviceId = lastWriterDeviceId,
                presetId = presetId,
                flows = flows.toList(),
                contacts = contacts.toList(),
                presetOverrides = presetOverrides,
            )
        )
    }

    // ─── PresetSettings serialization (spec 009 FR-013) ──────────────────

    private fun serializePresetSettings(ps: PresetSettings): JsonObject = buildJsonObject {
        if (ps.phoneHealthSettings != null) {
            put("phoneHealthSettings", serializePhoneHealthSettings(ps.phoneHealthSettings))
        }
    }

    private fun deserializePresetSettings(obj: JsonObject): Outcome<PresetSettings, BackendError> {
        val phs = (obj["phoneHealthSettings"] as? JsonObject)?.let {
            when (val r = deserializePhoneHealthSettings(it)) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> return r
            }
        }
        return Outcome.Success(PresetSettings(phoneHealthSettings = phs))
    }

    private fun serializePhoneHealthSettings(s: PhoneHealthSettings): JsonObject = buildJsonObject {
        put("batteryWarningPercent", s.batteryWarningPercent)
        put("batteryCriticalPercent", s.batteryCriticalPercent)
        put("lastSeenWarningHours", s.lastSeenWarningHours)
        put("lastSeenCriticalHours", s.lastSeenCriticalHours)
        put("audioMutedSeverity", s.audioMutedSeverity.wireValue)
        put("connectivityNoneSeverity", s.connectivityNoneSeverity.wireValue)
        put("updateCadenceInfoSec", s.updateCadenceInfoSec)
        put("pushAdminOnCritical", s.pushAdminOnCritical)
    }

    private fun deserializePhoneHealthSettings(obj: JsonObject): Outcome<PhoneHealthSettings, BackendError> {
        fun missing(name: String) = Outcome.Failure<BackendError>(
            BackendError.Unknown("phoneHealthSettings missing $name"),
        )
        val batteryWarn = obj["batteryWarningPercent"]?.jsonPrimitive?.intOrNull
            ?: return missing("batteryWarningPercent")
        val batteryCrit = obj["batteryCriticalPercent"]?.jsonPrimitive?.intOrNull
            ?: return missing("batteryCriticalPercent")
        val lastSeenWarn = obj["lastSeenWarningHours"]?.jsonPrimitive?.intOrNull
            ?: return missing("lastSeenWarningHours")
        val lastSeenCrit = obj["lastSeenCriticalHours"]?.jsonPrimitive?.intOrNull
            ?: return missing("lastSeenCriticalHours")
        val audioStr = obj["audioMutedSeverity"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return missing("audioMutedSeverity")
        val audio = SeverityWire.entries.firstOrNull { it.wireValue == audioStr }
            ?: return Outcome.Failure(BackendError.Unknown("audioMutedSeverity unknown: $audioStr"))
        val connStr = obj["connectivityNoneSeverity"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return missing("connectivityNoneSeverity")
        val conn = SeverityWire.entries.firstOrNull { it.wireValue == connStr }
            ?: return Outcome.Failure(BackendError.Unknown("connectivityNoneSeverity unknown: $connStr"))
        val cadence = obj["updateCadenceInfoSec"]?.jsonPrimitive?.intOrNull
            ?: return missing("updateCadenceInfoSec")
        val push = obj["pushAdminOnCritical"]?.jsonPrimitive?.booleanOrNull
            ?: return missing("pushAdminOnCritical")
        return Outcome.Success(
            PhoneHealthSettings(
                batteryWarningPercent = batteryWarn,
                batteryCriticalPercent = batteryCrit,
                lastSeenWarningHours = lastSeenWarn,
                lastSeenCriticalHours = lastSeenCrit,
                audioMutedSeverity = audio,
                connectivityNoneSeverity = conn,
                updateCadenceInfoSec = cadence,
                pushAdminOnCritical = push,
            ),
        )
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun parseServerTimestamp(el: JsonElement?): ServerTimestamp? {
        val obj = el as? JsonObject ?: return null
        val s = obj["epochSeconds"]?.jsonPrimitive?.longOrNull ?: return null
        val n = obj["nanoseconds"]?.jsonPrimitive?.intOrNull ?: 0
        return ServerTimestamp(epochSeconds = s, nanoseconds = n)
    }

    private fun serializeFlow(flow: Flow): JsonObject = buildJsonObject {
        put("id", flow.id.value)
        put("title", flow.title)
        put("slots", JsonArray(flow.slots.map { serializeSlot(it) }))
    }

    private fun deserializeFlow(el: JsonElement): Outcome<Flow, BackendError> {
        val obj = el as? JsonObject
            ?: return Outcome.Failure(BackendError.Unknown("flow is not a JsonObject"))
        val idStr = obj["id"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("flow missing id"))
        val id = runCatching { ElementId(idStr) }.getOrElse {
            return Outcome.Failure(BackendError.Unknown("flow id is not valid UUID: $idStr"))
        }
        val title = obj["title"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("flow missing title"))
        val slotsArr = obj["slots"] as? JsonArray ?: JsonArray(emptyList())
        val slots = mutableListOf<Slot>()
        for (e in slotsArr) {
            when (val r = deserializeSlot(e)) {
                is Outcome.Success -> slots.add(r.value)
                is Outcome.Failure -> return Outcome.Failure(r.error)
            }
        }
        return Outcome.Success(Flow(id = id, title = title, slots = slots.toList()))
    }

    private fun serializeSlot(slot: Slot): JsonObject = buildJsonObject {
        put("id", slot.id.value)
        put("kind", slot.kind.wireValue)
        if (slot.args != null) put("args", slot.args)
    }

    private fun deserializeSlot(el: JsonElement): Outcome<Slot, BackendError> {
        val obj = el as? JsonObject
            ?: return Outcome.Failure(BackendError.Unknown("slot is not a JsonObject"))
        val idStr = obj["id"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("slot missing id"))
        val id = runCatching { ElementId(idStr) }.getOrElse {
            return Outcome.Failure(BackendError.Unknown("slot id is not valid UUID: $idStr"))
        }
        val kindStr = obj["kind"]?.jsonPrimitive?.takeIf { it.isString }?.content
        val kind = SlotKind.fromWireOrNull(kindStr)
            ?: return Outcome.Failure(BackendError.Unknown("slot kind unknown: $kindStr"))
        val args = obj["args"] as? JsonObject
        return Outcome.Success(Slot(id = id, kind = kind, args = args))
    }

    private fun serializeContact(c: Contact): JsonObject = buildJsonObject {
        put("id", c.id.value)
        put("displayName", c.displayName)
        put("phoneNumber", c.phoneNumber)
        if (c.photoRef != null) put("photoRef", c.photoRef)
    }

    private fun deserializeContact(el: JsonElement): Outcome<Contact, BackendError> {
        val obj = el as? JsonObject
            ?: return Outcome.Failure(BackendError.Unknown("contact is not a JsonObject"))
        val idStr = obj["id"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("contact missing id"))
        val id = runCatching { ElementId(idStr) }.getOrElse {
            return Outcome.Failure(BackendError.Unknown("contact id is not valid UUID: $idStr"))
        }
        val displayName = obj["displayName"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("contact missing displayName"))
        val phoneNumber = obj["phoneNumber"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("contact missing phoneNumber"))
        val photoRef = obj["photoRef"]?.jsonPrimitive?.takeIf { it.isString }?.content
        return Outcome.Success(Contact(
            id = id,
            displayName = displayName,
            phoneNumber = phoneNumber,
            photoRef = photoRef,
        ))
    }
}
