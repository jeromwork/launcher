package com.launcher.api.config

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ServerTimestampTest {

    @Test
    fun compareTo_orders_by_epochSeconds_then_nanos() {
        val a = ServerTimestamp(epochSeconds = 1000L, nanoseconds = 100)
        val b = ServerTimestamp(epochSeconds = 1000L, nanoseconds = 200)
        val c = ServerTimestamp(epochSeconds = 1001L, nanoseconds = 0)

        assertTrue(a < b)
        assertTrue(b < c)
        assertTrue(a < c)
    }

    @Test
    fun compareTo_equal_timestamps_returns_zero() {
        val a = ServerTimestamp(epochSeconds = 1000L, nanoseconds = 100)
        val b = ServerTimestamp(epochSeconds = 1000L, nanoseconds = 100)
        assertEquals(0, a.compareTo(b))
    }

    @Test
    fun never_is_less_than_any_real_timestamp() {
        val real = ServerTimestamp(epochSeconds = 1L, nanoseconds = 0)
        assertTrue(ServerTimestamp.Never < real)
    }
}
