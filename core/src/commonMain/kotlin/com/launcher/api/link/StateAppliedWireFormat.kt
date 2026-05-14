package com.launcher.api.link

import com.launcher.api.config.ElementId
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.config.SlotKind
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Wire format для `/links/{linkId}/state/current` extended schema (spec 008
 * §FR-031..033, contracts/state-applied.md).
 *
 * **Additive extension** of спека 007 `LinkBootstrapWireFormat`:
 *  - schemaVersion stays at 1 (FR-032);
 *  - spec 007 readers (`LinkBootstrapWireFormat.deserialize`) read only their
 *    subset of fields, ignoring spec 008 extensions;
 *  - this class reads ALL fields including spec 008 extensions.
 *
 * Tests covered: roundtrip (bootstrap-only + full), backward-compat (spec 007
 * reader gracefully handles spec 008 docs), partial-apply serialization.
 */
object StateAppliedWireFormat {
    const val CURRENT_SCHEMA_VERSION: Int = StateApplied.SCHEMA_VERSION

    fun parseSchemaVersionOnly(json: JsonElement): Int? {
        val obj = json as? JsonObject ?: return null
        return obj["schemaVersion"]?.jsonPrimitive?.intOrNull
    }

    fun serialize(state: StateApplied): JsonObject = buildJsonObject {
        // Spec 007 baseline fields (kept identical с LinkBootstrapWireFormat).
        put("schemaVersion", CURRENT_SCHEMA_VERSION)
        put("appliedAt", state.appliedAt)
        put("presetId", state.presetId)
        if (state.fcmToken != null) put("fcmToken", state.fcmToken)
        put("updatedAt", state.updatedAt)

        // Spec 008 additive fields — omitted when null/empty for clean wire.
        if (state.appliedConfigUpdatedAt != null) {
            put("appliedConfigUpdatedAt", buildJsonObject {
                put("epochSeconds", state.appliedConfigUpdatedAt.epochSeconds)
                put("nanoseconds", state.appliedConfigUpdatedAt.nanoseconds)
            })
        }
        if (state.flowsApplied != null) {
            put("flowsApplied", JsonArray(state.flowsApplied.map { serializeFlowApplied(it) }))
        }
        if (state.contactsApplied != null) {
            put("contactsApplied", JsonArray(state.contactsApplied.map { serializeContactApplied(it) }))
        }
        if (state.partialApplyReasons.isNotEmpty()) {
            put("partialApplyReasons", JsonArray(state.partialApplyReasons.map { reason ->
                kotlinx.serialization.json.JsonPrimitive(reason.name)
            }))
        }
    }

    fun deserialize(json: JsonElement): Outcome<StateApplied, BackendError> {
        val obj = json as? JsonObject
            ?: return Outcome.Failure(BackendError.Unknown("state-applied payload is not a JsonObject"))

        val version = obj["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: return Outcome.Failure(BackendError.Unknown("state-applied missing schemaVersion"))

        if (version > CURRENT_SCHEMA_VERSION) {
            return Outcome.Failure(BackendError.Unknown(
                "state-applied schemaVersion=$version > supported $CURRENT_SCHEMA_VERSION"
            ))
        }

        val appliedAt = obj["appliedAt"]?.jsonPrimitive?.longOrNull
            ?: return Outcome.Failure(BackendError.Unknown("missing appliedAt"))
        val presetId = obj["presetId"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("missing presetId"))
        val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull
            ?: return Outcome.Failure(BackendError.Unknown("missing updatedAt"))
        val fcmToken = obj["fcmToken"]?.jsonPrimitive?.takeIf { it.isString }?.content

        // Spec 008 additive fields — all optional; null если absent.
        val appliedConfigUpdatedAt = (obj["appliedConfigUpdatedAt"] as? JsonObject)?.let { ts ->
            val s = ts["epochSeconds"]?.jsonPrimitive?.longOrNull ?: return@let null
            val n = ts["nanoseconds"]?.jsonPrimitive?.intOrNull ?: 0
            ServerTimestamp(epochSeconds = s, nanoseconds = n)
        }

        val flowsApplied = (obj["flowsApplied"] as? JsonArray)?.let { arr ->
            val list = mutableListOf<FlowApplied>()
            for (e in arr) {
                when (val r = deserializeFlowApplied(e)) {
                    is Outcome.Success -> list.add(r.value)
                    is Outcome.Failure -> return r
                }
            }
            list.toList()
        }

        val contactsApplied = (obj["contactsApplied"] as? JsonArray)?.let { arr ->
            val list = mutableListOf<ContactApplied>()
            for (e in arr) {
                when (val r = deserializeContactApplied(e)) {
                    is Outcome.Success -> list.add(r.value)
                    is Outcome.Failure -> return r
                }
            }
            list.toList()
        }

        val partialApplyReasons = (obj["partialApplyReasons"] as? JsonArray)?.mapNotNull { e ->
            val name = e.jsonPrimitive.takeIf { it.isString }?.content ?: return@mapNotNull null
            runCatching { PartialReason.valueOf(name) }.getOrNull()
        } ?: emptyList()

        return Outcome.Success(StateApplied(
            schemaVersion = version,
            appliedAt = appliedAt,
            presetId = presetId,
            fcmToken = fcmToken,
            updatedAt = updatedAt,
            appliedConfigUpdatedAt = appliedConfigUpdatedAt,
            flowsApplied = flowsApplied,
            contactsApplied = contactsApplied,
            partialApplyReasons = partialApplyReasons,
        ))
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun serializeFlowApplied(f: FlowApplied): JsonObject = buildJsonObject {
        put("id", f.id.value)
        put("title", f.title)
        put("slots", JsonArray(f.slots.map { serializeSlotApplied(it) }))
    }

    private fun deserializeFlowApplied(el: JsonElement): Outcome<FlowApplied, BackendError> {
        val obj = el as? JsonObject
            ?: return Outcome.Failure(BackendError.Unknown("flowApplied not JsonObject"))
        val idStr = obj["id"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("flowApplied missing id"))
        val id = runCatching { ElementId(idStr) }.getOrElse {
            return Outcome.Failure(BackendError.Unknown("flowApplied id not UUID"))
        }
        val title = obj["title"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("flowApplied missing title"))
        val slotsArr = obj["slots"] as? JsonArray ?: JsonArray(emptyList())
        val slots = mutableListOf<SlotApplied>()
        for (e in slotsArr) {
            when (val r = deserializeSlotApplied(e)) {
                is Outcome.Success -> slots.add(r.value)
                is Outcome.Failure -> return Outcome.Failure(r.error)
            }
        }
        return Outcome.Success(FlowApplied(id = id, title = title, slots = slots.toList()))
    }

    private fun serializeSlotApplied(s: SlotApplied): JsonObject = buildJsonObject {
        put("id", s.id.value)
        put("kind", s.kind.wireValue)
        put("appliedSuccessfully", s.appliedSuccessfully)
    }

    private fun deserializeSlotApplied(el: JsonElement): Outcome<SlotApplied, BackendError> {
        val obj = el as? JsonObject
            ?: return Outcome.Failure(BackendError.Unknown("slotApplied not JsonObject"))
        val idStr = obj["id"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("slotApplied missing id"))
        val id = runCatching { ElementId(idStr) }.getOrElse {
            return Outcome.Failure(BackendError.Unknown("slotApplied id not UUID"))
        }
        val kindStr = obj["kind"]?.jsonPrimitive?.takeIf { it.isString }?.content
        val kind = SlotKind.fromWireOrNull(kindStr)
            ?: return Outcome.Failure(BackendError.Unknown("slotApplied unknown kind: $kindStr"))
        val applied = obj["appliedSuccessfully"]?.jsonPrimitive?.let {
            it.content.toBooleanStrictOrNull()
        } ?: return Outcome.Failure(BackendError.Unknown("slotApplied missing appliedSuccessfully"))
        return Outcome.Success(SlotApplied(id = id, kind = kind, appliedSuccessfully = applied))
    }

    private fun serializeContactApplied(c: ContactApplied): JsonObject = buildJsonObject {
        put("id", c.id.value)
        put("displayName", c.displayName)
        put("appliedSuccessfully", c.appliedSuccessfully)
    }

    private fun deserializeContactApplied(el: JsonElement): Outcome<ContactApplied, BackendError> {
        val obj = el as? JsonObject
            ?: return Outcome.Failure(BackendError.Unknown("contactApplied not JsonObject"))
        val idStr = obj["id"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("contactApplied missing id"))
        val id = runCatching { ElementId(idStr) }.getOrElse {
            return Outcome.Failure(BackendError.Unknown("contactApplied id not UUID"))
        }
        val displayName = obj["displayName"]?.jsonPrimitive?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(BackendError.Unknown("contactApplied missing displayName"))
        val applied = obj["appliedSuccessfully"]?.jsonPrimitive?.let {
            it.content.toBooleanStrictOrNull()
        } ?: return Outcome.Failure(BackendError.Unknown("contactApplied missing appliedSuccessfully"))
        return Outcome.Success(ContactApplied(id = id, displayName = displayName, appliedSuccessfully = applied))
    }
}
