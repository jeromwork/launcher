package com.launcher.app.data.envelope

import family.keys.api.Envelope
import family.keys.api.Outcome
import family.keys.api.internal.EnvelopeStorage
import family.keys.api.internal.EnvelopeStorageError
import java.util.concurrent.ConcurrentHashMap

/**
 * mockBackend / dev-build implementation of [EnvelopeStorage] — keeps envelopes
 * in process memory. Used in non-GMS / dev / preview app variants without a
 * Firestore project.
 *
 * Caller-side behaviour is identical to [com.launcher.app.data.envelope.FirestoreEnvelopeStorage]:
 * the surface contract is the same, only persistence differs.
 *
 * Data is lost on process death — acceptable for the variant scope.
 */
class InMemoryEnvelopeStorage : EnvelopeStorage {

    private val store = ConcurrentHashMap<String, ConcurrentHashMap<String, Envelope>>()

    override suspend fun store(
        namespace: String,
        key: String,
        envelope: Envelope
    ): Outcome<Unit, EnvelopeStorageError> {
        if (namespace.isEmpty()) return Outcome.Failure(EnvelopeStorageError.Unauthorized)
        if (key.isEmpty()) return Outcome.Failure(EnvelopeStorageError.Malformed("key must not be empty"))
        store.computeIfAbsent(namespace) { ConcurrentHashMap() }[key] = envelope
        return Outcome.Success(Unit)
    }

    override suspend fun load(
        namespace: String,
        key: String
    ): Outcome<Envelope, EnvelopeStorageError> {
        val envelope = store[namespace]?.get(key)
            ?: return Outcome.Failure(EnvelopeStorageError.NotFound)
        return Outcome.Success(envelope)
    }

    override suspend fun list(
        namespace: String,
        keyPrefix: String
    ): Outcome<List<String>, EnvelopeStorageError> {
        val keys = store[namespace]?.keys
            ?.filter { it.startsWith(keyPrefix) }
            ?.sorted()
            ?: emptyList()
        return Outcome.Success(keys)
    }

    override suspend fun delete(
        namespace: String,
        key: String
    ): Outcome<Unit, EnvelopeStorageError> {
        store[namespace]?.remove(key)
        return Outcome.Success(Unit)
    }
}
