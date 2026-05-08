package com.launcher.core

import android.content.Context
import com.launcher.api.FlowRepository
import com.launcher.api.ModuleDescriptor
import com.launcher.api.PresetRepository
import com.launcher.api.action.ProviderId
import com.launcher.core.actions.ActionCycleGuard
import com.launcher.core.actions.ActionDispatcher
import com.launcher.core.actions.AndroidActionDispatcher
import com.launcher.core.actions.CommunicationConfigValidator
import com.launcher.core.actions.ReturnContextStore
import com.launcher.core.actions.WhatsAppLaunchabilityResolver
import com.launcher.core.actions.handlers.ActionHandler
import com.launcher.core.actions.handlers.AppLaunchHandler
import com.launcher.core.actions.handlers.BrowserHandler
import com.launcher.core.actions.handlers.HandlerContext
import com.launcher.core.actions.handlers.PhoneHandler
import com.launcher.core.actions.handlers.SmsHandler
import com.launcher.core.actions.handlers.SystemSettingsHandler
import com.launcher.core.actions.handlers.TelegramHandler
import com.launcher.core.actions.handlers.WhatsAppHandler
import com.launcher.core.actions.handlers.YouTubeHandler
import com.launcher.core.bridge.SystemEventBridge
import com.launcher.core.catalog.AppIndex
import com.launcher.core.contacts.MockContactsRepository
import com.launcher.core.events.CommunicationDiagnostics
import com.launcher.core.events.EventRouter
import com.launcher.core.flows.MockFlowRepository
import com.launcher.core.modules.ModuleRegistry
import com.launcher.core.preset.InMemoryPresetRepository
import com.launcher.core.profile.ProfileEngine
import com.launcher.core.providers.AndroidProviderRegistry
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
    flowRepository: FlowRepository? = null,
    presetRepository: PresetRepository? = null,
) {
    private val appContext = context.applicationContext
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    val presetRepository: PresetRepository = presetRepository ?: InMemoryPresetRepository()
    val flowRepository: FlowRepository = flowRepository ?: MockFlowRepository(appContext, this.presetRepository)
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

    /**
     * Spec 005 dispatcher pipeline. Lives alongside the legacy [actionDispatcher]
     * until the UI is migrated to call into it (Phase 5 — `dispatchAction(Action)`
     * replaces the `dispatchAction(ActionRequest)` callback). Phase 6 then deletes
     * the legacy class. Eager construction is intentional for `AppLaunchHandler`
     * / `SystemSettingsHandler` per plan §Risks row 5: cold-start budget
     * absorbs handler `init` cost once, not per-tap.
     */
    val mockContactsRepository: MockContactsRepository = MockContactsRepository(appContext)
    val androidProviderRegistry: AndroidProviderRegistry =
        AndroidProviderRegistry(context = appContext, appIndex = appIndex, scope = scope)
    private val handlers: Map<ProviderId, ActionHandler> = mapOf(
        ProviderId.APP             to AppLaunchHandler(),
        ProviderId.WHATSAPP        to WhatsAppHandler(mockContactsRepository),
        ProviderId.TELEGRAM        to TelegramHandler(),
        ProviderId.PHONE           to PhoneHandler(),
        ProviderId.SMS             to SmsHandler(),
        ProviderId.BROWSER         to BrowserHandler(),
        ProviderId.YOUTUBE         to YouTubeHandler(),
        ProviderId.SYSTEM_SETTINGS to SystemSettingsHandler(),
    )
    val androidActionDispatcher: com.launcher.api.action.ActionDispatcher =
        AndroidActionDispatcher(
            handlers = handlers,
            providerRegistry = androidProviderRegistry,
            eventRouter = eventRouter,
            handlerContext = HandlerContext(
                context = appContext,
                packageManager = appContext.packageManager,
                eventRouter = eventRouter,
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
