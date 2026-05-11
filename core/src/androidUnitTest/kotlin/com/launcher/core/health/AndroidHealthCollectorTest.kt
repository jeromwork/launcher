package com.launcher.core.health

import androidx.test.core.app.ApplicationProvider
import com.launcher.api.health.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [AndroidHealthCollector] initial snapshot construction.
 *
 * Robolectric provides shadow ConnectivityManager / AudioManager / battery
 * broadcast — defaults are: connectivity=None, ringer=0%, no charging, etc.
 * Полный coverage callback paths (NetworkCallback onAvailable triggering
 * snapshot rebuild) — задача спека 010 integration tests, в спеке 006 здесь
 * только sanity-check of construction + initial snapshot shape.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidHealthCollectorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun construction_doesNotCrash() {
        // Smoke test: registering 5 system observers + initial read should
        // not throw, even в minimal Robolectric environment.
        val collector = AndroidHealthCollector(
            context = context,
            appVersion = "test-1.4.2",
            scope = scope,
        )
        val initial = collector.snapshot()
        assertNotNull(initial)
        assertEquals("test-1.4.2", initial.appVersion)
    }

    @Test
    fun initialSnapshot_hasSafeDefaults_inEmptyEnvironment() {
        val collector = AndroidHealthCollector(
            context = context,
            appVersion = "test",
            scope = scope,
        )
        val snapshot = collector.snapshot()
        // Robolectric не предоставляет реальную сеть → connectivity = None.
        assertEquals(Connectivity.None, snapshot.connectivity)
        // Battery в Robolectric обычно null/zero → safe default 0.
        assertTrue(snapshot.batteryPercent in 0..100, "batteryPercent must be 0..100, got ${snapshot.batteryPercent}")
        // ringerVolumePercent тоже в диапазоне.
        assertTrue(snapshot.ringerVolumePercent in 0..100)
        // lastSeen — recent (set to now in initialSnapshot).
        assertTrue(snapshot.lastSeen > 0L, "lastSeen must be set to construction time")
    }
}
