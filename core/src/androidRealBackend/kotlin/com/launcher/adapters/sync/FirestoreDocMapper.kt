package com.launcher.adapters.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.DocSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * Boundary mapper between Firestore SDK types and the domain
 * [DocSnapshot] / [JsonElement] surface (FR-013, spec 007 anti-corruption
 * layer).
 *
 * Why this exists: a `DocumentSnapshot.data` is `Map<String, Any?>` with
 * vendor types (`Timestamp`, `GeoPoint`, `DocumentReference`, …). Letting
 * those leak into commonMain would couple every domain consumer to
 * Firestore. We translate to `kotlinx.serialization.JsonElement` exactly
 * once, here, and the rest of the codebase reasons in JSON.
 *
 * Limitations (intentional, per CLAUDE.md §4 — MVA):
 *  - `GeoPoint`, `DocumentReference`, `Blob` map to a string representation
 *    or are dropped. Spec 007 does not use them; add support if a future
 *    spec needs them.
 *  - `Timestamp` collapses to epoch-millis [Long] (matches `Health.lastSeen`
 *    convention).
 */
internal object FirestoreDocMapper {

    /** Translate a Firestore [DocumentSnapshot] into a domain [DocSnapshot].
     *  Returns `null` when the document does not exist. */
    fun fromFirestore(path: DocPath, snapshot: DocumentSnapshot): DocSnapshot? {
        if (!snapshot.exists()) return null
        val data = snapshot.data ?: return null
        val json = mapToJson(data) as? JsonObject
            ?: return null
        val schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 0
        val updatedAt = (data["updatedAt"] as? Timestamp)?.toMillis()
        return DocSnapshot(
            path = path,
            data = json,
            schemaVersion = schemaVersion,
            updatedAt = updatedAt,
            isStale = snapshot.metadata.isFromCache && snapshot.metadata.hasPendingWrites(),
        )
    }

    /** Convert a domain JSON body into a `Map<String, Any?>` payload for
     *  Firestore. Numbers stay as Number, booleans as Boolean, strings as
     *  String, null as null. Nested objects/arrays recurse. */
    fun toFirestore(json: JsonElement): Map<String, Any?> {
        val obj = json as? JsonObject
            ?: error("Top-level Firestore document body must be a JsonObject; got ${json::class.simpleName}")
        return obj.mapValues { (_, v) -> jsonElementToAny(v) }
    }

    /** Maps a Firestore SDK exception to a domain [BackendError]. The
     *  SDK throws subtypes of [com.google.firebase.firestore.FirebaseFirestoreException]
     *  with a [com.google.firebase.firestore.FirebaseFirestoreException.Code]
     *  enum we route on. */
    fun mapException(throwable: Throwable): BackendError {
        val ff = throwable as? com.google.firebase.firestore.FirebaseFirestoreException
            ?: return BackendError.Unknown(throwable.message ?: throwable::class.simpleName ?: "unknown")
        return when (ff.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> BackendError.Offline
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> BackendError.PermissionDenied
            com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND -> BackendError.NotFound
            com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED,
            com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                BackendError.TransactionConflict(ff.message ?: "conflict")
            com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> BackendError.Offline
            else -> BackendError.Unknown(ff.message ?: ff.code.name)
        }
    }

    // ---- internals -------------------------------------------------------

    private fun mapToJson(map: Map<String, Any?>): JsonElement =
        JsonObject(map.mapValues { (_, v) -> anyToJsonElement(v) })

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Timestamp -> JsonPrimitive(value.toMillis())
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) ->
            k.toString() to anyToJsonElement(v)
        })
        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonObject -> element.mapValues { (_, v) -> jsonElementToAny(v) }
        is JsonArray -> element.map { jsonElementToAny(it) }
    }
}

private fun Timestamp.toMillis(): Long = seconds * 1_000L + nanoseconds / 1_000_000L
