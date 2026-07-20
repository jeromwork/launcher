package com.launcher.api.edit

import com.launcher.wire.WireVersion
import com.launcher.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

import com.launcher.api.result.Outcome
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Wire format (de)serializer для [NamedConfig] + container envelope used by
 * [NamedConfigsLocalStore] persistence (T056 DataStore real adapter).
 *
 * **Fail-closed forward-compat** per contracts/named-config-local.md §Forward
 * compatibility and `docs/architecture/wire-format.md` §4: a document declaring a
 * `minReaderVersion` above ours returns [StoreError.UnsupportedSchemaVersion]. Named configs are
 * consequential enough that silently dropping unknown fields would corrupt user data (losing the
 * `isDefault` flag from a newer payload would break the single-default invariant FR-003a).
 *
 * Container envelope (envelope JSON):
 * ```json
 * {
 *   "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
 *   "configs": [ ...NamedConfig... ]
 * }
 * ```
 */
object NamedConfigWireFormat {

    /**
     * Container envelope — its own wire format, versioned independently of the [NamedConfig]
     * documents it carries. The two version headers are deliberate: the container can gain
     * fields without touching the configs inside, and vice versa.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class Envelope(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val schemaVersion: WireVersion = SCHEMA_VERSION,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val minReaderVersion: WireVersion = MIN_READER_VERSION,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
        val configs: List<NamedConfig> = emptyList(),
    ) : WireVersionHeader

    /** What this build writes for the container. Was the integer 1 — never lowered (I3). */
    val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

    /** The container is a list wrapper; additions to it are ignorable by an older reader. */
    val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

    /** Rewritten wholesale on every save; no partial-merge writer exists. */
    val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)

    /** Serializes container envelope to compact JSON. */
    fun serialize(envelope: Envelope): String =
        WireFormatJson.json.encodeToString(envelope)

    /**
     * Deserializes container envelope from JSON, fail-closed on:
     *  - unparseable JSON ([StoreError.UnsupportedSchemaVersion] with a null `required`).
     *  - the container declaring a `minReaderVersion` above ours.
     *  - any carried config declaring a `minReaderVersion` above ours.
     *
     * A missing `minReaderVersion` reads as "1.0" — earlier dev builds did not stamp it, and the
     * field is additive per §5.
     */
    fun deserialize(json: String): Outcome<Envelope, StoreError> {
        // Read the version header before the body (wire-format.md §4 — never decode on a guess).
        val tree = try {
            WireFormatJson.json.parseToJsonElement(json).jsonObject
        } catch (e: SerializationException) {
            return Outcome.Failure(unsupported(required = null))
        } catch (e: IllegalArgumentException) {
            return Outcome.Failure(unsupported(required = null))
        }

        // The container gates on its own header; each carried config gates on its own. A config
        // needing a newer reader fails the whole read rather than being dropped silently — losing
        // one of an admin's named configs without saying so is worse than refusing the file.
        tree.refusalReason(SCHEMA_VERSION)?.let { return Outcome.Failure(it) }
        val configsArray = tree["configs"] as? JsonArray
        configsArray?.forEach { element ->
            val inner = element as? JsonObject ?: return@forEach
            inner.refusalReason(NamedConfig.SCHEMA_VERSION)?.let { return Outcome.Failure(it) }
        }

        return try {
            val envelope = WireFormatJson.json.decodeFromString<Envelope>(json)
            Outcome.Success(envelope)
        } catch (e: SerializationException) {
            Outcome.Failure(unsupported(required = tree.peekMinReaderVersion()))
        }
    }

    private fun unsupported(required: WireVersion?): StoreError.UnsupportedSchemaVersion =
        StoreError.UnsupportedSchemaVersion(required = required, readerLevel = SCHEMA_VERSION)

    /**
     * Returns the error to fail with when this object cannot be read by [readerLevel], or null
     * when it can. An absent `minReaderVersion` is treated as "readable by v1" — earlier dev
     * builds did not stamp it, and the field is additive per §5.
     */
    private fun JsonObject.refusalReason(readerLevel: WireVersion): StoreError? {
        val minReader = peekMinReaderVersion() ?: WireVersion(1, 0)
        return if (readerLevel < minReader) {
            StoreError.UnsupportedSchemaVersion(required = minReader, readerLevel = readerLevel)
        } else {
            null
        }
    }

    private fun JsonObject.peekMinReaderVersion(): WireVersion? =
        (this["minReaderVersion"] as? JsonPrimitive)?.contentOrNull
            ?.let { WireVersion.parseOrNull(it) }
}
