package cryptokit.keys.fakes

import cryptokit.keys.api.Envelope
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.internal.EnvelopeStorage
import cryptokit.keys.api.internal.EnvelopeStorageError

/**
 * In-memory [EnvelopeStorage] for tests. Per-namespace map of `key → envelope`.
 */
internal class FakeEnvelopeStorage : EnvelopeStorage {

    private val store = mutableMapOf<String, MutableMap<String, Envelope>>()

    fun snapshot(): Map<String, Map<String, Envelope>> =
        store.mapValues { (_, m) -> m.toMap() }

    override suspend fun store(
        namespace: String,
        key: String,
        envelope: Envelope
    ): Outcome<Unit, EnvelopeStorageError> {
        store.getOrPut(namespace) { mutableMapOf() }[key] = envelope
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
