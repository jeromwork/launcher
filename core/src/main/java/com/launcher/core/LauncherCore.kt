package com.launcher.core

import android.content.Context
import com.launcher.api.ModuleDescriptor
import com.launcher.core.actions.ActionCycleGuard
import com.launcher.core.actions.ActionDispatcher
import com.launcher.core.actions.AllowedAppsGate
import com.launcher.core.actions.CapabilitySnapshotResolver
import com.launcher.core.actions.CommunicationConfigValidator
import com.launcher.core.actions.ControlModeStore
import com.launcher.core.actions.ReturnContextStore
import com.launcher.core.actions.SafeControlConfigStore
import com.launcher.core.actions.EscapeProtectionPolicyEngine
import com.launcher.core.actions.WhatsAppLaunchabilityResolver
import com.launcher.core.bridge.SystemEventBridge
import com.launcher.core.catalog.AppIndex
import com.launcher.core.events.CommunicationDiagnostics
import com.launcher.core.events.EventRouter
import com.launcher.core.events.SafetyDiagnostics
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
    val safeControlConfigStore = SafeControlConfigStore(appContext)
    private val safeControlConfig = safeControlConfigStore.load()
    val controlModeStore = ControlModeStore(
        context = appContext,
        defaultMode = safeControlConfig.defaultMode,
    )
    val capabilitySnapshotResolver = CapabilitySnapshotResolver(
        context = appContext,
        modeStore = controlModeStore,
        accessibilityServiceClassName = "com.launcher.app.safety.SafeLauncherAccessibilityService",
    )
    val escapeProtectionPolicyEngine = EscapeProtectionPolicyEngine(capabilitySnapshotResolver)
    private val communicationConfigValidator = CommunicationConfigValidator(appContext)
    private val communicationDiagnostics = CommunicationDiagnostics(emitEvent = eventRouter::emit)
    val safetyDiagnostics = SafetyDiagnostics(emitEvent = eventRouter::emit)
    private val allowedAppsGate = AllowedAppsGate(
        policyProvider = {
            com.launcher.api.AllowedAppsPolicy(
                allowedPackages = safeControlConfig.allowedMessengerPackages,
                alwaysAllowedPackages = setOf(appContext.packageName),
            )
        },
    )
    val actionDispatcher = ActionDispatcher(
        context = appContext,
        appIndex = appIndex,
        actionCycleGuard = ActionCycleGuard(),
        returnContextStore = ReturnContextStore(appContext),
        configValidator = communicationConfigValidator,
        diagnostics = communicationDiagnostics,
        allowedAppsGate = allowedAppsGate,
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
