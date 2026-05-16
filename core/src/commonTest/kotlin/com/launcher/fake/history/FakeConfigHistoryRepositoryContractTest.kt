package com.launcher.fake.history

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigSnapshot
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.history.ConfigHistoryRepository
import com.launcher.api.result.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Spec 009 contract test for [ConfigHistoryRepository] fake (Phase 4).
 *
 * Behaviour locked here applies to BOTH fake and real Firestore adapter:
 *  - `readAll` returns DESC by `recordedAt`
 *  - `housekeep(retentionCount=N)` keeps newest N
 *  - idempotent housekeep — second call is a no-op
 */
class FakeConfigHistoryRepositoryContractTest {

    private fun snapshot(recordedAt: Long): ConfigSnapshot = ConfigSnapshot(
        config = ConfigDocument(
            serverUpdatedAt = ServerTimestamp(epochSeconds = recordedAt / 1000, nanoseconds = 0),
            lastWriterDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
            presetId = "p",
            flows = emptyList(),
            contacts = emptyList(),
        ),
        recordedAt = recordedAt,
        recordedFromDeviceId = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
    )

    @Test
    fun readAll_returns_desc_by_recordedAt() = runTest {
        val repo = FakeConfigHistoryRepository()
        repo.recordSnapshot("L1", snapshot(100L)).orFail()
        repo.recordSnapshot("L1", snapshot(300L)).orFail()
        repo.recordSnapshot("L1", snapshot(200L)).orFail()

        val all = (repo.readAll("L1") as Outcome.Success).value
        assertEquals(listOf(300L, 200L, 100L), all.map { it.snapshot.recordedAt })
    }

    @Test
    fun housekeep_keeps_newest_N() = runTest {
        val repo = FakeConfigHistoryRepository()
        repeat(15) { i -> repo.recordSnapshot("L1", snapshot(i.toLong())).orFail() }
        repo.housekeep("L1", retentionCount = 10).orFail()

        val all = (repo.readAll("L1") as Outcome.Success).value
        assertEquals(10, all.size)
        assertEquals(14L, all.first().snapshot.recordedAt)
        assertEquals(5L, all.last().snapshot.recordedAt)
    }

    @Test
    fun housekeep_is_idempotent() = runTest {
        val repo = FakeConfigHistoryRepository()
        repeat(3) { i -> repo.recordSnapshot("L1", snapshot(i.toLong())).orFail() }
        repo.housekeep("L1", retentionCount = 10).orFail()
        repo.housekeep("L1", retentionCount = 10).orFail()
        val all = (repo.readAll("L1") as Outcome.Success).value
        assertEquals(3, all.size)
    }

    @Test
    fun housekeep_below_retention_noop() = runTest {
        val repo = FakeConfigHistoryRepository()
        repo.recordSnapshot("L1", snapshot(1L)).orFail()
        repo.housekeep("L1", retentionCount = 10).orFail()
        val all = (repo.readAll("L1") as Outcome.Success).value
        assertEquals(1, all.size)
    }

    @Test
    fun separate_linkIds_isolated() = runTest {
        val repo = FakeConfigHistoryRepository()
        repo.recordSnapshot("L1", snapshot(1L)).orFail()
        repo.recordSnapshot("L2", snapshot(2L)).orFail()
        assertEquals(1, (repo.readAll("L1") as Outcome.Success).value.size)
        assertEquals(1, (repo.readAll("L2") as Outcome.Success).value.size)
    }

    private fun <T, E> Outcome<T, E>.orFail(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("expected Success, got Failure($error)")
    }
}
