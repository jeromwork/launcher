package com.launcher.app.preset.task126

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.launcher.app.boot.BootCheckReceiver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T086 — Receiver dispatch → WorkManager enqueue observed; the actual
 * `BootCheckWorker.doWork()` runs outside the receiver context (CL-9).
 *
 * We do NOT execute the worker body here (that requires the full Koin graph
 * with Firebase / crypto deps not available in JVM unit tests — covered by
 * T085 [`BootCheckReconcileTest`] at the engine level and by the deferred
 * physical-device smoke T087). We only verify the dispatch contract: the
 * receiver enqueues a unique OneTimeWorkRequest for `BootCheckWorker` and
 * returns synchronously.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class BootCheckWorkerTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        // WorkManagerTestInitHelper doesn't require explicit teardown; the
        // in-memory database is discarded when the Robolectric Application
        // is torn down between tests.
    }

    @Test
    fun receiverEnqueuesUniqueBootCheckWork() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val receiver = BootCheckReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val wm = WorkManager.getInstance(context)
        val infos = wm.getWorkInfosForUniqueWork(BootCheckReceiver.WORK_NAME).get()

        assertEquals(1, infos.size)
        assertTrue(
            "Expected ENQUEUED/RUNNING/SUCCEEDED, got ${infos[0].state}",
            infos[0].state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED),
        )
    }

    @Test
    fun receiverIgnoresUnrelatedActions() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val receiver = BootCheckReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_TICK))

        val wm = WorkManager.getInstance(context)
        val infos = wm.getWorkInfosForUniqueWork(BootCheckReceiver.WORK_NAME).get()
        assertTrue(infos.isEmpty())
    }

    @Test
    fun secondBootDoesNotDuplicateWork() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val receiver = BootCheckReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val wm = WorkManager.getInstance(context)
        val infos = wm.getWorkInfosForUniqueWork(BootCheckReceiver.WORK_NAME).get()
        // ExistingWorkPolicy.KEEP — second enqueue is a no-op, still 1 entry.
        assertEquals(1, infos.size)
    }
}
