package com.launcher.adapters.wizard

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.launcher.api.wizard.Clock
import com.launcher.api.wizard.SettingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SettingStatusCache] + [CacheInvalidatingLifecycleObserver] behavioural
 * tests per FR-021 / FR-022.
 *
 *  - cache hit within TTL → return stored value.
 *  - cache stale beyond TTL → return null (caller re-fetches).
 *  - lifecycle ON_RESUME → invalidateAll.
 *  - explicit invalidate(id) clears that id only.
 */
@RunWith(RobolectricTestRunner::class)
class CacheInvalidationTest {

    private class MovableClock(var nowMillis: Long = 0L) : Clock {
        override fun nowEpochMillis(): Long = nowMillis
    }

    @Test
    fun hit_withinTtl_returnsStoredValue() {
        val clock = MovableClock(nowMillis = 100L)
        val cache = SettingStatusCache(clock = clock, ttlMillis = 30_000L)
        cache.put("android.role.home", SettingStatus.Applied)

        clock.nowMillis = 100L + 29_000L // 1 s before TTL
        assertEquals(SettingStatus.Applied, cache.get("android.role.home"))
    }

    @Test
    fun miss_pastTtl_returnsNull() {
        val clock = MovableClock(nowMillis = 100L)
        val cache = SettingStatusCache(clock = clock, ttlMillis = 30_000L)
        cache.put("android.role.home", SettingStatus.Applied)

        clock.nowMillis = 100L + 30_001L // 1 ms past TTL
        assertNull(cache.get("android.role.home"))
    }

    @Test
    fun invalidate_singleId_clearsThatIdOnly() {
        val clock = MovableClock()
        val cache = SettingStatusCache(clock = clock)
        cache.put("a", SettingStatus.Applied)
        cache.put("b", SettingStatus.NotApplied)

        cache.invalidate("a")

        assertNull(cache.get("a"))
        assertEquals(SettingStatus.NotApplied, cache.get("b"))
    }

    @Test
    fun lifecycleObserver_onResume_invalidatesAll() {
        val clock = MovableClock()
        val cache = SettingStatusCache(clock = clock)
        cache.put("a", SettingStatus.Applied)
        cache.put("b", SettingStatus.NotApplied)

        val observer = CacheInvalidatingLifecycleObserver(cache)
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle = LifecycleRegistry(this).also {
                it.currentState = Lifecycle.State.RESUMED
            }
        }
        observer.onResume(owner)

        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
