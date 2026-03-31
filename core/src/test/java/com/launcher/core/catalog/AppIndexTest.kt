package com.launcher.core.catalog

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.launcher.core.events.EventRouter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppIndexTest {

    @Test
    fun refreshBuildsSnapshotFromInjectedPackageManager() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = mockk<PackageManager>()
        val launchable = ApplicationInfo().apply { packageName = "com.launchable" }
        val noLaunch = ApplicationInfo().apply { packageName = "com.widget.only" }
        every { pm.getInstalledApplications(PackageManager.GET_META_DATA) } returns listOf(launchable, noLaunch)
        every { pm.getLaunchIntentForPackage("com.launchable") } returns Intent()
        every { pm.getLaunchIntentForPackage("com.widget.only") } returns null

        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main.immediate)
        val router = EventRouter(scope)
        val index = AppIndex(app, scope, router, packageManager = pm)

        index.refreshNow()

        val snap = index.snapshot.value
        assertEquals(1, snap.entries.size)
        val entry = snap.entries.single()
        assertEquals("com.launchable", entry.stableKey)
        assertEquals("com.launchable", entry.displayLabel)
        assertTrue(entry.isLaunchable)
        assertEquals(1L, snap.generation)
    }
}
