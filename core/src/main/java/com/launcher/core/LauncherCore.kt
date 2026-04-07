package com.launcher.core

import android.content.Context
import com.launcher.api.ModuleDescriptor
import com.launcher.core.actions.ActionCycleGuard
import com.launcher.core.actions.ActionDispatcher
import com.launcher.core.actions.CommunicationConfigValidator
import com.launcher.core.actions.ReturnContextStore
import com.launcher.core.actions.WhatsAppLaunchabilityResolver
import com.launcher.core.bridge.SystemEventBridge
import com.launcher.core.catalog.AppIndex
import com.launcher.core.events.CommunicationDiagnostics
import com.launcher.core.events.EventRouter
import com.launcher.core.modules.ModuleRegistry
import com.launcher.core.profile.ProfileEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

/**
 * Application-scoped facade: wires router, profile, catalog, dispatch, and OS bridge.
 * Call [start] / [stop] with process lifecycle (explicit bridge registration).
 */
class LauncherCore(
    context: Context,
    moduleDescriptors: List<ModuleDescriptor> = emptyList(),
    skipPackageScan: Boolean = false,
) {
    private val appContext = context.applicationContext
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    val eventRouter = EventRouter(scope)
    val moduleRegistry = ModuleRegistry(moduleDescriptors)
    val profileEngine = ProfileEngine(appContext, moduleRegistry, eventRouter)
    val appIndex = AppIndex(appContext, scope, eventRouter, skipPackageScan = skipPackageScan)
    private val communicationConfigValidator = CommunicationConfigValidator(appContext)
    private val communicationDiagnostics = CommunicationDiagnostics(emitEvent = eventRouter::emit)
    val actionDispatcher = ActionDispatcher(
        context = appContext,
        appIndex = appIndex,
        actionCycleGuard = ActionCycleGuard(),
        returnContextStore = ReturnContextStore(appContext),
        configValidator = communicationConfigValidator,
        diagnostics = communicationDiagnostics,
        launchabilityResolver = WhatsAppLaunchabilityResolver(
            context = appContext,
            configValidator = communicationConfigValidator,
        ),
    )
    private val systemEventBridge = SystemEventBridge(appContext, eventRouter)

    fun start() {
        profileEngine.loadFromAssets()
        appIndex.start()
        systemEventBridge.register()
    }

    fun stop() {
        systemEventBridge.unregister()
        appIndex.stop()
        job.cancelChildren()
    }
}
