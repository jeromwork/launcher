package family.keys.fakes

import family.keys.api.PassphraseAttemptCounter

/**
 * In-memory [PassphraseAttemptCounter] для unit-тестов. Production использует
 * Android DataStore (T122f, Lane B).
 *
 * [resetTimeoutMillis]: после этого таймаута с lastAttemptAt counter resets.
 * Default 1 час (как production), но тесты могут override на 0 для skipping.
 */
class InMemoryAttemptCounter(
    private val resetTimeoutMillis: Long = 3_600_000L,
    private val clock: () -> Long = { 0L }
) : PassphraseAttemptCounter {

    private data class Entry(var count: Int, var lastAttemptAt: Long)
    private val store = mutableMapOf<String, Entry>()

    override suspend fun currentCount(uid: String): Int = store[uid]?.count ?: 0

    override suspend fun recordFailedAttempt(uid: String): Int {
        val e = store.getOrPut(uid) { Entry(0, clock()) }
        e.count += 1
        e.lastAttemptAt = clock()
        return e.count
    }

    override suspend fun resetIfExpired(uid: String) {
        val e = store[uid] ?: return
        if (clock() - e.lastAttemptAt > resetTimeoutMillis) {
            store.remove(uid)
        }
    }

    override suspend fun clear(uid: String) {
        store.remove(uid)
    }
}
