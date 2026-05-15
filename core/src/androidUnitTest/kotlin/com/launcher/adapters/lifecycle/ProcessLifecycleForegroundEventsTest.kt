package com.launcher.adapters.lifecycle

import com.launcher.api.config.ConfigSyncConstants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for time-throttle logic of [ProcessLifecycleForegroundEvents].
 *
 * Per spec 008 Phase 7 T093 / FR-022 T4: rapid lifecycle ON_RESUME should
 * collapse to ≤ 1 emit per `RESUMED_TRIGGER_THROTTLE_MS`.
 *
 * **Note**: full lifecycle integration (ProcessLifecycleOwner) requires
 * Robolectric Application + instrumented Lifecycle observer setup, which
 * is brittle in unit tests. Here we test the **throttle math** directly:
 * we replicate the filter logic from ProcessLifecycleForegroundEvents and
 * assert the gate. Phase 11 in-process E2E will exercise the full chain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProcessLifecycleForegroundEventsTest {

    @Test
    fun throttle_allows_first_emit() {
        var now = 1_000_000L
        var lastEmit = Long.MIN_VALUE
        val throttle = 120_000L

        val allowed = throttleGate(now, lastEmit, throttle)
        assertTrue(allowed)
    }

    @Test
    fun throttle_blocks_within_window() {
        val throttle = 120_000L
        var lastEmit = 1_000_000L
        val now = lastEmit + throttle - 1
        val allowed = throttleGate(now, lastEmit, throttle)
        assertEquals(false, allowed, "Within throttle window — must block")
    }

    @Test
    fun throttle_allows_after_window() {
        val throttle = 120_000L
        var lastEmit = 1_000_000L
        val now = lastEmit + throttle + 1
        val allowed = throttleGate(now, lastEmit, throttle)
        assertTrue(allowed, "After window — must allow")
    }

    @Test
    fun throttle_constant_is_2_minutes() {
        assertEquals(2L * 60_000L, ConfigSyncConstants.RESUMED_TRIGGER_THROTTLE_MS)
    }

    private fun throttleGate(nowMs: Long, lastEmitMs: Long, throttleMs: Long): Boolean {
        // If lastEmit hasn't happened yet (sentinel), allow.
        if (lastEmitMs == Long.MIN_VALUE) return true
        return nowMs - lastEmitMs >= throttleMs
    }
}
