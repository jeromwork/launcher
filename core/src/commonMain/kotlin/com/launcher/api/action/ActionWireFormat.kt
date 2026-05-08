package com.launcher.api.action

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wire-format encode/decode for [Action] per
 * [`contracts/action-wire-format.md`](specs/005-action-architecture-v2/contracts/action-wire-format.md)
 * v1.0.0.
 *
 * Test fixtures: `core/src/commonTest/resources/fixtures/action-wire-format/`
 * (per spec 005 Clarification C4).
 *
 * Forward-compat policy:
 *  - Unknown fields inside payload are ignored ([Json.ignoreUnknownKeys] = true).
 *  - Unknown providerId parses successfully — surfaces at dispatch as
 *    [DispatchResult.ProviderUnavailable] with [UnavailabilityHint.UnknownInThisVersion].
 *  - Unknown payload `kind` discriminator throws SerializationException at parse;
 *    callers ([AndroidActionDispatcher]) translate to [DispatchResult.Failure].
 *  - `schemaVersion > [Action.SUPPORTED_SCHEMA_VERSION]` parses (not blocked here);
 *    rejected at dispatch time, NOT at parse, so caller can decide what to log.
 */
object ActionWireFormat {

    /**
     * The JSON instance used for encode/decode. Stable across all spec 005
     * sites (mock asset readers, test fixtures, future backend deserialiser).
     *
     * - `classDiscriminator = "kind"` — matches `payload.kind` per the contract.
     * - `ignoreUnknownKeys = true` — forward-compat: newer producers may add fields.
     * - `encodeDefaults = false` — null `fallback`, null `sourceModuleId` stay
     *   off the wire (smaller diffs, no `"fallback": null` noise).
     */
    val json: Json = Json {
        classDiscriminator = "kind"
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    /** Pretty-printing variant for fixture generation and human-readable export. */
    val prettyJson: Json = Json(from = json) {
        prettyPrint = true
    }

    fun encode(action: Action): String = json.encodeToString(Action.serializer(), action)

    fun encodePretty(action: Action): String = prettyJson.encodeToString(Action.serializer(), action)

    fun decode(jsonString: String): Action = json.decodeFromString(Action.serializer(), jsonString)

    /**
     * One-time bridge from spec 003 mock wire format (un-versioned, ad-hoc
     * `SlotAction` discriminator) to spec 005 [Action] wire format.
     *
     * **Lifecycle:** this function exists only until spec 006 lands. Fitness
     * function `legacyMigrationExpiryTest` (Phase 7) deletes it from the
     * source tree once `migrateLegacyActionDeadlineSpec` (root build script)
     * matches the latest merged spec id. See spec 005 Clarification C5.
     *
     * LEGACY-BRIDGE-EXPIRES-IN-SPEC-006 — search anchor for grep / Konsist test.
     *
     * Supported legacy shapes (input):
     * - `{ "type": "whatsapp_call", "contactRef": "alice", "actionType": "voice" }`
     * - `{ "type": "whatsapp_call", "contactRef": "alice", "actionType": "video" }`
     * - `{ "type": "open_app", "packageName": "com.example" }`
     * - `{ "type": "placeholder" }` -> returns `null` (empty slot, not an action)
     *
     * Legacy input that already matches the new shape (`schemaVersion: 1`,
     * `providerId`, …) is parsed straight through.
     *
     * @return new [Action], or `null` if the legacy entry was a placeholder.
     * @throws IllegalArgumentException for unrecognised legacy shapes.
     */
    fun migrateLegacyAction(legacyJsonString: String): Action? {
        val element = json.parseToJsonElement(legacyJsonString).jsonObject

        // Already in new wire format -> parse directly. Detected by presence of
        // either `schemaVersion` (when written by encodePretty / external producer)
        // or `providerId` (default-omitted schemaVersion via encodeDefaults=false).
        val hasSchemaVersion = element["schemaVersion"]?.jsonPrimitive?.intOrNull != null
        val hasProviderId    = element["providerId"]?.jsonPrimitive?.contentOrNull != null
        if (hasSchemaVersion || hasProviderId) return decode(legacyJsonString)

        val type = element["type"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("legacy action missing 'type': $legacyJsonString")

        return when (type) {
            "placeholder" -> null

            "open_app" -> {
                val pkg = requireString(element, "packageName", "open_app")
                Action(
                    providerId = ProviderId.APP,
                    payload = ActionPayload.OpenApp(packageHint = pkg),
                )
            }

            "whatsapp_call" -> {
                val contactRef = requireString(element, "contactRef", "whatsapp_call")
                val actionType = requireString(element, "actionType", "whatsapp_call")
                val callKind = when (actionType.lowercase()) {
                    "voice", "call" -> WhatsAppCallKind.VOICE
                    "video"         -> WhatsAppCallKind.VIDEO
                    else -> throw IllegalArgumentException("unknown legacy actionType: '$actionType'")
                }
                Action(
                    providerId = ProviderId.WHATSAPP,
                    payload = ActionPayload.WhatsAppCall(contactRef, callKind),
                )
            }

            "whatsapp_message" -> {
                val contactRef = requireString(element, "contactRef", "whatsapp_message")
                Action(
                    providerId = ProviderId.WHATSAPP,
                    payload = ActionPayload.WhatsAppMessage(contactRef),
                )
            }

            else -> throw IllegalArgumentException("unrecognised legacy action type: '$type'")
        }
    }

    private fun requireString(obj: JsonObject, key: String, ctx: String): String =
        obj[key]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("legacy '$ctx' missing field '$key'")
}
