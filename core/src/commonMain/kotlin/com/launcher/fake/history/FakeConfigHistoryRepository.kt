package com.launcher.fake.history

import com.launcher.api.config.ConfigSnapshot
import com.launcher.api.history.ConfigHistoryRepository
import com.launcher.api.history.ConfigSnapshotWithId
import com.launcher.api.history.RepositoryError
import com.launcher.api.result.Outcome

/**
 * In-memory fake for [ConfigHistoryRepository] (spec 009 Phase 4 mock-first
 * per CLAUDE.md §6). Sorted DESC by `recordedAt`; auto-generated ids are
 * deterministic "snap-N" so tests can assert ordering. Not thread-safe —
 * tests call from a single coroutine.
 */
class FakeConfigHistoryRepository : ConfigHistoryRepository {

    private val storage = mutableMapOf<String, MutableList<ConfigSnapshotWithId>>()
    private var seq = 0

    override suspend fun recordSnapshot(
        linkId: String,
        snapshot: ConfigSnapshot,
    ): Outcome<Unit, RepositoryError> {
        val list = storage.getOrPut(linkId) { mutableListOf() }
        list.add(0, ConfigSnapshotWithId(autoId = "snap-${++seq}", snapshot = snapshot))
        list.sortByDescending { it.snapshot.recordedAt }
        return Outcome.Success(Unit)
    }

    override suspend fun readAll(
        linkId: String,
    ): Outcome<List<ConfigSnapshotWithId>, RepositoryError> {
        val list = storage[linkId].orEmpty().sortedByDescending { it.snapshot.recordedAt }
        return Outcome.Success(list)
    }

    override suspend fun housekeep(
        linkId: String,
        retentionCount: Int,
    ): Outcome<Unit, RepositoryError> {
        val list = storage[linkId] ?: return Outcome.Success(Unit)
        if (list.size <= retentionCount) return Outcome.Success(Unit)
        list.sortByDescending { it.snapshot.recordedAt }
        val toKeep = list.take(retentionCount)
        list.clear()
        list.addAll(toKeep)
        return Outcome.Success(Unit)
    }
}
