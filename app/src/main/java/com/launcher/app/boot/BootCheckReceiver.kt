package com.launcher.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * TASK-126 Phase 4 T080 (FR-012, CL-9) — dispatch-only receiver for
 * `BOOT_COMPLETED`. Enqueues a `BootCheckWorker` via WorkManager and
 * returns within the ~10-second ANR window.
 *
 * The receiver MUST NOT do any preset / DataStore / IO work inline —
 * cold-boot on slow devices (Xiaomi Redmi Note 11 in particular) has
 * exceeded 10 s in prior benchmarks. WorkManager guarantees the actual
 * reconcile runs off the receiver context on a background thread.
 */
class BootCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "BOOT_COMPLETED received, enqueueing BootCheckWorker")
        val request = OneTimeWorkRequestBuilder<BootCheckWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "boot-check"
        private const val TAG = "BootCheckReceiver"
    }
}
