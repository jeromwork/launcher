package family.push.internal

import family.push.api.PushPayload
import family.push.api.WireFormatVersion

/**
 * T016 — Encode/decode [PushPayload] ↔ `Map<String, String>` (FCM data-message
 * format). Per spec 019 contracts/push-payload-v1.md.
 *
 * FCM constraint: `data` payload — flat `Map<String, String>`. Cannot serialize
 * nested `fields: Map<String, String>` directly. Strategy (per contract §Encoding):
 *   • Reserved top-level keys: `schemaVersion`, `eventType`, `ownerUid`,
 *     `triggerId`, `linkId`.
 *   • Nested `fields` map promoted via `field_<key>` prefix:
 *       `fields["configName"] = "main"` → `field_configName = "main"`.
 *
 * Forward-compat policy (receiver side):
 *   • `schemaVersion` missing OR not int → null (drop).
 *   • `schemaVersion > MAX_SUPPORTED` → null (silent log + ignore, fail-soft).
 *   • `eventType` missing → null (drop).
 *   • `triggerId` missing → null (debounce key required).
 *   • Unknown reserved key → ignored (additive).
 */
internal object PushPayloadWireFormat {

    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_EVENT_TYPE = "eventType"
    private const val KEY_OWNER_UID = "ownerUid"
    private const val KEY_TRIGGER_ID = "triggerId"
    private const val KEY_LINK_ID = "linkId"
    private const val FIELD_PREFIX = "field_"

    private val RESERVED_KEYS = setOf(
        KEY_SCHEMA_VERSION, KEY_EVENT_TYPE, KEY_OWNER_UID,
        KEY_TRIGGER_ID, KEY_LINK_ID,
    )

    /** Cheap version probe — used before full parse. */
    fun parseSchemaVersionOnly(data: Map<String, String>): Int? =
        data[KEY_SCHEMA_VERSION]?.toIntOrNull()

    /** Encode для Worker → FCM dispatch. Все значения становятся String. */
    fun encode(payload: PushPayload): Map<String, String> = buildMap {
        put(KEY_SCHEMA_VERSION, payload.schemaVersion.toString())
        put(KEY_EVENT_TYPE, payload.eventType)
        payload.ownerUid?.let { put(KEY_OWNER_UID, it) }
        put(KEY_TRIGGER_ID, payload.triggerId)
        payload.linkId?.let { put(KEY_LINK_ID, it) }
        payload.fields.forEach { (key, value) ->
            put(FIELD_PREFIX + key, value)
        }
    }

    /**
     * Parse flat data-map → PushPayload. Returns null на любом invariant
     * violation. Receiver MUST drop silently на null (FR-075).
     */
    fun parse(data: Map<String, String>): PushPayload? {
        val version = data[KEY_SCHEMA_VERSION]?.toIntOrNull() ?: return null
        // Forward-compat: future schemaVersion → silent ignore at receiver (fail-soft).
        if (version > WireFormatVersion.MAX_SUPPORTED_SCHEMA_VERSION) return null
        val eventType = data[KEY_EVENT_TYPE] ?: return null
        val triggerId = data[KEY_TRIGGER_ID] ?: return null
        val ownerUid = data[KEY_OWNER_UID]
        val linkId = data[KEY_LINK_ID]

        val fields = buildMap {
            data.forEach { (key, value) ->
                if (key.startsWith(FIELD_PREFIX)) {
                    put(key.removePrefix(FIELD_PREFIX), value)
                }
            }
        }

        return PushPayload(
            schemaVersion = version,
            eventType = eventType,
            ownerUid = ownerUid,
            triggerId = triggerId,
            fields = fields,
            linkId = linkId,
        )
    }
}
