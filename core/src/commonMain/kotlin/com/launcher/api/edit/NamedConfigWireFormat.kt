package com.launcher.api.edit

import com.launcher.api.result.Outcome
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Wire format (de)serializer для [NamedConfig] + container envelope used by
 * [NamedConfigsLocalStore] persistence (T056 DataStore real adapter).
 *
 * **Fail-closed forward-compat** per contracts/named-config-local.md §Forward
 * compatibility: reads of JSON с `schemaVersion > CURRENT_SCHEMA_VERSION`
 * return [StoreError.UnsupportedSchemaVersion]. Named configs are
 * consequential enough that silently dropping unknown fields would corrupt
 * user data (e.g., losing the `isDefault` flag from a v2 payload would break
 * the single-default invariant FR-003a).
 *
 * Container envelope (envelope JSON):
 * ```json
 * { "schemaVersion": 1, "configs": [ ...NamedConfig... ] }
 * ```
 */
object NamedConfigWireFormat {

    /** Container envelope serializable; `schemaVersion` separate из container's own version. */
    @Serializable
    data class Envelope(
        val schemaVersion: Int = NamedConfig.CURRENT_SCHEMA_VERSION,
        val configs: List<NamedConfig> = emptyList(),
    )

    /** Serializes container envelope to compact JSON. */
    fun serialize(envelope: Envelope): String =
        WireFormatJson.json.encodeToString(envelope)

    /**
     * Deserializes container envelope from JSON, fail-closed on:
     *  - unparseable JSON ([StoreError.UnsupportedSchemaVersion] with `found = -1`).
     *  - `schemaVersion > CURRENT_SCHEMA_VERSION` ([StoreError.UnsupportedSchemaVersion]).
     *  - inner config's `schemaVersion > CURRENT_SCHEMA_VERSION`.
     *
     * Missing `schemaVersion` field is interpreted as v1 (additive forward-compat
     * — earlier dev builds may not have stamped it).
     */
    fun deserialize(json: String): Outcome<Envelope, StoreError> {
        // Peek schemaVersion первым по wire-format CHK002 (read FIRST).
        val tree = try {
            WireFormatJson.json.parseToJsonElement(json).jsonObject
        } catch (e: SerializationException) {
            return Outcome.Failure(
                StoreError.UnsupportedSchemaVersion(
                    found = -1,
                    supported = NamedConfig.CURRENT_SCHEMA_VERSION,
                ),
            )
        } catch (e: IllegalArgumentException) {
            return Outcome.Failure(
                StoreError.UnsupportedSchemaVersion(
                    found = -1,
                    supported = NamedConfig.CURRENT_SCHEMA_VERSION,
                ),
            )
        }

        val containerVersion = tree.peekSchemaVersion() ?: NamedConfig.CURRENT_SCHEMA_VERSION
        if (containerVersion > NamedConfig.CURRENT_SCHEMA_VERSION) {
            return Outcome.Failure(
                StoreError.UnsupportedSchemaVersion(
                    found = containerVersion,
                    supported = NamedConfig.CURRENT_SCHEMA_VERSION,
                ),
            )
        }

        // Check each inner config's schemaVersion too.
        val configsArray = tree["configs"] as? JsonArray
        configsArray?.forEach { element ->
            val inner = element as? JsonObject ?: return@forEach
            val v = inner.peekSchemaVersion() ?: NamedConfig.CURRENT_SCHEMA_VERSION
            if (v > NamedConfig.CURRENT_SCHEMA_VERSION) {
                return Outcome.Failure(
                    StoreError.UnsupportedSchemaVersion(
                        found = v,
                        supported = NamedConfig.CURRENT_SCHEMA_VERSION,
                    ),
                )
            }
        }

        return try {
            val envelope = WireFormatJson.json.decodeFromString<Envelope>(json)
            Outcome.Success(envelope)
        } catch (e: SerializationException) {
            Outcome.Failure(
                StoreError.UnsupportedSchemaVersion(
                    found = containerVersion,
                    supported = NamedConfig.CURRENT_SCHEMA_VERSION,
                ),
            )
        }
    }

    private fun JsonObject.peekSchemaVersion(): Int? =
        (this["schemaVersion"] as? JsonPrimitive)?.intOrNull
}
