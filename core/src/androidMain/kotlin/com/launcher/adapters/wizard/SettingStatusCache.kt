package com.launcher.adapters.wizard

import com.launcher.api.wizard.Clock
import com.launcher.api.wizard.SettingStatus

/**
 * TTL cache for [SettingStatus] lookups per data-model.md §4.1 / FR-021.
 *
 * Invalidation: time-bound (default 30 s) + explicit per-id (after a
 * successful apply) + bulk-clear on `Lifecycle.Event.ON_RESUME` (via
 * [CacheInvalidatingLifecycleObserver]).
 *
 * TODO(perf): if call sites move off the main coroutine context, wrap
 * [entries] in a `Mutex` — current MutableMap is not thread-safe. Today
 * every caller stays on the UI dispatcher per Article IX §3.
 */
class SettingStatusCache(
    private val clock: Clock,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private val entries: MutableMap<String, Pair<SettingStatus, Long>> = mutableMapOf()

    fun get(settingId: String): SettingStatus? {
        val (status, recordedAt) = entries[settingId] ?: return null
        val age = clock.nowEpochMillis() - recordedAt
        return if (age > ttlMillis) null else status
    }

    fun put(settingId: String, status: SettingStatus) {
        entries[settingId] = status to clock.nowEpochMillis()
    }

    fun invalidate(settingId: String) {
        entries.remove(settingId)
    }

    fun invalidateAll() {
        entries.clear()
    }

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 30_000L
    }
}
