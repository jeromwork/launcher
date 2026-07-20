package com.launcher.adapters.history

import com.launcher.api.config.SnapshotMigrator
import family.wire.WireVersion

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigDocumentWireFormat
import com.launcher.api.config.ConfigSnapshot
import com.launcher.api.history.ConfigHistoryRepository
import com.launcher.api.history.ConfigSnapshotWithId
import com.launcher.api.history.RepositoryError
import com.launcher.api.result.Outcome
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Firestore implementation of [ConfigHistoryRepository] (spec 009
 * FR-036..FR-038).
 *
 * Path note: spec text uses `/links/{linkId}/config/history/{autoId}`
 * shorthand; canonical Firestore path is sibling collection
 * `/links/{linkId}/configHistory/{autoId}` (see TODO-DOC-001 в
 * project-backlog.md, firestore.rules). This adapter writes/reads from
 * `configHistory`.
 *
 * Konsist gate (T101): `com.google.firebase.*` stays in this file;
 * commonMain port surfaces only [ConfigSnapshot] (CLAUDE.md rule 1).
 *
 * TODO(server-roadmap SRV-CONFIG-001): server-side write replaces
 * client recordedFromDeviceId trust + makes publish+history atomic.
 *
 * TODO(server-roadmap SRV-CONFIG-002): server-side housekeeping cron
 * replaces client-orchestrated delete.
 */
class FirestoreConfigHistoryAdapter(
    private val firestore: FirebaseFirestore,
) : ConfigHistoryRepository {

    override suspend fun recordSnapshot(
        linkId: String,
        snapshot: ConfigSnapshot,
    ): Outcome<Unit, RepositoryError> = try {
        val map = jsonObjectToMap(serializeSnapshot(snapshot))
        firestore.collection(linksPath(linkId))
            .add(map)
            .await()
        Outcome.Success(Unit)
    } catch (e: FirebaseFirestoreException) {
        mapFirestoreError(e)
    } catch (e: Throwable) {
        Outcome.Failure(RepositoryError.BackendUnavailable(e))
    }

    override suspend fun readAll(
        linkId: String,
    ): Outcome<List<ConfigSnapshotWithId>, RepositoryError> {
        return try {
            val snap = firestore.collection(linksPath(linkId))
                .orderBy("recordedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            val result = mutableListOf<ConfigSnapshotWithId>()
            for (doc in snap.documents) {
                val data = doc.data ?: continue
                val json = mapToJsonObject(data)
                when (val parsed = deserializeSnapshot(json)) {
                    is Outcome.Success -> result.add(ConfigSnapshotWithId(doc.id, parsed.value))
                    is Outcome.Failure -> return Outcome.Failure(parsed.error)
                }
            }
            Outcome.Success(result)
        } catch (e: FirebaseFirestoreException) {
            mapFirestoreError(e)
        } catch (e: Throwable) {
            Outcome.Failure(RepositoryError.BackendUnavailable(e))
        }
    }

    override suspend fun housekeep(
        linkId: String,
        retentionCount: Int,
    ): Outcome<Unit, RepositoryError> {
        return try {
            val snap = firestore.collection(linksPath(linkId))
                .orderBy("recordedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            if (snap.size() <= retentionCount) return Outcome.Success(Unit)
            val toDelete = snap.documents.drop(retentionCount)
            for (doc in toDelete) {
                doc.reference.delete().await()
            }
            Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            mapFirestoreError(e)
        } catch (e: Throwable) {
            Outcome.Failure(RepositoryError.BackendUnavailable(e))
        }
    }

    private fun linksPath(linkId: String): String = "links/$linkId/configHistory"

    private fun <T> mapFirestoreError(e: FirebaseFirestoreException): Outcome<T, RepositoryError> = when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            Outcome.Failure(RepositoryError.PermissionDenied(e.message ?: "denied"))
        else ->
            Outcome.Failure(RepositoryError.BackendUnavailable(e))
    }

    private fun serializeSnapshot(snapshot: ConfigSnapshot): JsonObject = buildJsonObject {
        put("snapshotSchemaVersion", snapshot.schemaVersion.toString())
        put("snapshotMinReaderVersion", snapshot.minReaderVersion.toString())
        put("snapshotMinWriterVersion", snapshot.minWriterVersion.toString())
        put("recordedAt", snapshot.recordedAt)
        put("recordedFromDeviceId", snapshot.recordedFromDeviceId)
        put("config", ConfigDocumentWireFormat.serialize(snapshot.config))
    }

    private fun deserializeSnapshot(json: JsonObject): Outcome<ConfigSnapshot, RepositoryError> {
        val ver = json["snapshotSchemaVersion"]?.jsonPrimitive?.contentOrNull
            ?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(RepositoryError.Corrupt(IllegalStateException("missing/unreadable snapshotSchemaVersion")))
        val minReader = json["snapshotMinReaderVersion"]?.jsonPrimitive?.contentOrNull
            ?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(RepositoryError.Corrupt(IllegalStateException("missing/unreadable snapshotMinReaderVersion")))
        val minWriter = json["snapshotMinWriterVersion"]?.jsonPrimitive?.contentOrNull
            ?.let { WireVersion.parseOrNull(it) }
            ?: return Outcome.Failure(RepositoryError.Corrupt(IllegalStateException("missing/unreadable snapshotMinWriterVersion")))
        val recordedAt = json["recordedAt"]?.jsonPrimitive?.longOrNull
            ?: return Outcome.Failure(RepositoryError.Corrupt(IllegalStateException("missing recordedAt")))
        val recordedFromDeviceId = json["recordedFromDeviceId"]?.jsonPrimitive
            ?.takeIf { it.isString }?.content
            ?: return Outcome.Failure(RepositoryError.Corrupt(IllegalStateException("missing recordedFromDeviceId")))
        val configObj = json["config"] as? JsonObject
            ?: return Outcome.Failure(RepositoryError.Corrupt(IllegalStateException("missing config")))
        val config: ConfigDocument = when (val r = ConfigDocumentWireFormat.deserialize(configObj)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(
                RepositoryError.Corrupt(IllegalStateException("config deser failed: ${r.error}")),
            )
        }
        val snapshot = ConfigSnapshot(
            schemaVersion = ver,
            minReaderVersion = minReader,
            minWriterVersion = minWriter,
            config = config,
            recordedAt = recordedAt,
            recordedFromDeviceId = recordedFromDeviceId,
        )
        // Apply the version gate. Until TASK-138 this call was missing entirely: SnapshotMigrator
        // documented a fail-closed reader policy and had zero production callers, so the read path
        // accepted any envelope version. The guarantee existed only in the KDoc.
        return when (val gated = SnapshotMigrator.migrate(snapshot)) {
            is Outcome.Success -> Outcome.Success(gated.value)
            is Outcome.Failure -> Outcome.Failure(
                RepositoryError.Corrupt(
                    IllegalStateException("snapshot needs a newer reader: ${gated.error}"),
                ),
            )
        }
    }

    // ─── Firestore <-> JsonObject helpers ───────────────────────────────

    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject =
        JsonObject(map.mapValues { (_, v) -> anyToJsonElement(v) })

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Timestamp -> JsonPrimitive(value.toDate().time)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) },
        )
        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> =
        obj.mapValues { (_, v) -> jsonElementToFirestoreValue(v) }

    private fun jsonElementToFirestoreValue(el: JsonElement): Any? = when (el) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            el.isString -> el.content
            else -> {
                val asLong = el.content.toLongOrNull()
                if (asLong != null) asLong
                else el.content.toDoubleOrNull()
                    ?: el.content.toBooleanStrictOrNull()
                    ?: el.content
            }
        }
        is JsonObject -> jsonObjectToMap(el)
        is JsonArray -> el.map { jsonElementToFirestoreValue(it) }
    }
}
