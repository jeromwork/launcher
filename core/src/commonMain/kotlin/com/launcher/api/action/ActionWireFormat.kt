package com.launcher.api.action

import kotlinx.serialization.json.Json

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
}
