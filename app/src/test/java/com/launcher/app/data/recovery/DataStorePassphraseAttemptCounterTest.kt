package com.launcher.app.data.recovery

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric тесты для [DataStorePassphraseAttemptCounter] (T122h, T122i, H-1 acceptance).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class DataStorePassphraseAttemptCounterTest {

    private fun makeCounter(now: () -> Long = { 1_000L }, timeout: Long = 3_600_000L) =
        DataStorePassphraseAttemptCounter(
            context = ApplicationProvider.getApplicationContext(),
            resetTimeoutMillis = timeout,
            now = now
        )

    @Test
    fun freshCounterIsZero() = runBlocking {
        val counter = makeCounter()
        assertEquals(0, counter.currentCount("uid-x"))
    }

    @Test
    fun recordFailedAttemptIncrements() = runBlocking {
        val counter = makeCounter()
        assertEquals(1, counter.recordFailedAttempt("uid-x"))
        assertEquals(2, counter.recordFailedAttempt("uid-x"))
        assertEquals(3, counter.recordFailedAttempt("uid-x"))
        assertEquals(3, counter.currentCount("uid-x"))
    }

    @Test
    fun differentUidsTrackedIndependently() = runBlocking {
        val counter = makeCounter()
        counter.recordFailedAttempt("uid-a")
        counter.recordFailedAttempt("uid-a")
        counter.recordFailedAttempt("uid-b")
        assertEquals(2, counter.currentCount("uid-a"))
        assertEquals(1, counter.currentCount("uid-b"))
    }

    @Test
    fun clearResetsCounter() = runBlocking {
        val counter = makeCounter()
        counter.recordFailedAttempt("uid-x")
        counter.recordFailedAttempt("uid-x")
        counter.clear("uid-x")
        assertEquals(0, counter.currentCount("uid-x"))
    }

    @Test
    fun resetIfExpiredClearsAfterTimeout() = runBlocking {
        var clockNow = 1000L
        val counter = makeCounter(now = { clockNow }, timeout = 1000L)
        counter.recordFailedAttempt("uid-x")
        assertEquals(1, counter.currentCount("uid-x"))
        // 0.5s later — still locked.
        clockNow = 1500L
        counter.resetIfExpired("uid-x")
        assertEquals(1, counter.currentCount("uid-x"))
        // 2.5s later — expired, reset.
        clockNow = 3500L
        counter.resetIfExpired("uid-x")
        assertEquals(0, counter.currentCount("uid-x"))
    }

    @Test
    fun maxAttemptsDefaultIs3() {
        val counter = makeCounter()
        assertEquals(3, counter.maxAttempts)
    }
}
