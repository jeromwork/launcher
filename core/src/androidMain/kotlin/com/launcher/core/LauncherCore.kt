package com.launcher.core

import android.content.Context
import com.launcher.api.FlowRepository
import com.launcher.api.ModuleDescriptor
import com.launcher.api.PresetRepository
import com.launcher.api.action.ProviderId
import com.launcher.core.actions.AndroidActionDispatcher
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
import com.launcher.core.events.EventRouter
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
    // Spec 010 T032a (ARCH-016 closure): flowRepository is **mandatory** —
    // MockFlowRepository default removed. Caller (Application / Koin DI) must
    // supply the `ConfigBackedFlowRepository` adapter from spec 008
    // ConfigEditor + LinkRegistry. Tests inject a FakeRemoteSyncBackend-wired
    // editor + registry; production wires the Firestore-backed pair.
    flowRepository: FlowRepository,
    moduleDescriptors: List<ModuleDescriptor> = emptyList(),
    skipPackageScan: Boolean = false,
    presetRepository: PresetRepository? = null,
) {
    private val appContext = context.applicationContext
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    val presetRepository: PresetRepository = presetRepository ?: InMemoryPresetRepository()
    val flowRepository: FlowRepository = flowRepository
    val eventRouter = EventRouter(scope)
    val moduleRegistry = ModuleRegistry(moduleDescriptors)
    val profileEngine = ProfileEngine(appContext, moduleRegistry, eventRouter)
    val appIndex = AppIndex(appContext, scope, eventRouter, skipPackageScan = skipPackageScan)

    val mockContactsRepository: MockContactsRepository = MockContactsRepository(appContext)
    val androidProviderRegistry: AndroidProviderRegistry =
        AndroidProviderRegistry(context = appContext, appIndex = appIndex, scope = scope)

    /**
     * Eagerly constructed handler set. Cold-start budget absorbs `init` cost
     * once, not per-tap (plan §Risks row 5). Each handler is stateless and
     * safe to share across coroutines.
     */
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
        cleanupSpec002Residue()
    }

    fun stop() {
        systemEventBridge.unregister()
        appIndex.stop()
        job.cancelChildren()
    }

    /**
     * One-shot SharedPreferences cleanup of `launcher.communication.return_context`
     * left over from spec 002 (FEATURE-RETURN-CONTEXT-REMOVED-IN-SPEC-005).
     * Spec 005 §5.3 / research R3. Idempotent: running twice is harmless.
     * Will be removed when the legacy bridge expires (spec 006 per Clarification C5).
     */
    private fun cleanupSpec002Residue() {
        val prefs = appContext.getSharedPreferences(
            "launcher.communication.return_context", Context.MODE_PRIVATE,
        )
        if (prefs.all.isNotEmpty()) prefs.edit().clear().apply()
    }
}
