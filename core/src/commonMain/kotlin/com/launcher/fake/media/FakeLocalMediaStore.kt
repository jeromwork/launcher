package com.launcher.fake.media

import com.launcher.api.media.LocalMediaFile
import com.launcher.api.media.LocalMediaStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spec 012 fake — in-memory [LocalMediaStore] для tests / mockBackend variant.
 *
 * Backed by mutable map. Persistent across calls within the same test but
 * **not across test class instances** (no actual disk). Use this для unit tests;
 * use real [FileLocalMediaStore] (androidMain) для integration tests на эмуляторе.
 *
 * Per CLAUDE.md §6 (mock-first development).
 *
 * Task: T1216 (Phase 2).
 */
class FakeLocalMediaStore(
    /** Test-injected clock — monotonically increasing per call. Defaults to counter for determinism. */
    private val nowMillis: () -> Long = run {
        var counter = 0L
        ({ ++counter })
    },
) : LocalMediaStore {
    private val mutex = Mutex()
    private val store = mutableMapOf<String, ByteArray>()
    private val lastAccessedAt = mutableMapOf<String, Long>()

    override suspend fun read(uuid: String): LocalMediaFile? = mutex.withLock {
        val bytes = store[uuid] ?: return null
        val now = nowMillis()
        lastAccessedAt[uuid] = now
        InMemoryLocalMediaFile(bytes, now)
    }

    override suspend fun write(uuid: String, bytes: ByteArray): LocalMediaFile = mutex.withLock {
        val copy = bytes.copyOf()
        store[uuid] = copy
        val now = nowMillis()
        lastAccessedAt[uuid] = now
        InMemoryLocalMediaFile(copy, now)
    }

    override suspend fun delete(uuid: String) {
        mutex.withLock {
            store.remove(uuid)
            lastAccessedAt.remove(uuid)
        }
    }

    override suspend fun exists(uuid: String): Boolean = mutex.withLock {
        store.containsKey(uuid)
    }

    override suspend fun totalSizeBytes(): Long = mutex.withLock {
        store.values.sumOf { it.size.toLong() }
    }

    /** Test helper — snapshot of all uuids currently in store. */
    suspend fun keys(): Set<String> = mutex.withLock { store.keys.toSet() }

    /** Test helper — clear all entries. */
    suspend fun clear() {
        mutex.withLock {
            store.clear()
            lastAccessedAt.clear()
        }
    }
}

private class InMemoryLocalMediaFile(
    private val bytes: ByteArray,
    override val lastAccessedAtEpochMillis: Long,
) : LocalMediaFile {
    override val sizeBytes: Long = bytes.size.toLong()
    override suspend fun readBytes(): ByteArray = bytes.copyOf()
}
