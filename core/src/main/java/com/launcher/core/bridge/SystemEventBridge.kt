package com.launcher.core.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.launcher.api.PackageChangeReason
import com.launcher.api.ProjectEvent
import com.launcher.core.events.EventRouter

/**
 * Registers package broadcasts and forwards normalized events to [EventRouter].
 *
 * MVP listener source, thread, frequency, and fallback: [PLATFORM_EVENTS.md](../../../../../../../../specs/001-launcher-core-foundation/PLATFORM_EVENTS.md)
 * (repo root `specs/001-launcher-core-foundation/PLATFORM_EVENTS.md`; research §6 is the normative source — update research first, then that doc).
 */
class SystemEventBridge(
    private val context: Context,
    private val eventRouter: EventRouter,
) {
    private val appContext = context.applicationContext

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val reason = when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> PackageChangeReason.PACKAGE_ADDED
                Intent.ACTION_PACKAGE_REMOVED -> PackageChangeReason.PACKAGE_REMOVED
                Intent.ACTION_PACKAGE_REPLACED -> PackageChangeReason.PACKAGE_REPLACED
                Intent.ACTION_PACKAGE_CHANGED -> PackageChangeReason.PACKAGE_CHANGED
                Intent.ACTION_MY_PACKAGE_REPLACED -> PackageChangeReason.MY_PACKAGE_REPLACED
                else -> null
            }
            eventRouter.emit(ProjectEvent.PackageSetChanged(reason = reason))
        }
    }

    private val packageFilter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addDataScheme("package")
    }

    private val myPackageFilter = IntentFilter(Intent.ACTION_MY_PACKAGE_REPLACED)

    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        register(packageFilter)
        register(myPackageFilter)
    }

    fun unregister() {
        if (!registered) return
        registered = false
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun register(filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
    }
}
