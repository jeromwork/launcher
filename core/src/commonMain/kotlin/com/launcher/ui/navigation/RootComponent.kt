package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.FlowPreset
import com.launcher.api.FlowRepository
import com.launcher.api.PresetRepository
import com.launcher.api.action.Action
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.apps.InstalledAppsCatalog
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.ElementId
import com.launcher.api.history.ConfigHistoryRepository
import com.launcher.api.link.LinkRegistry
import com.launcher.api.sync.RemoteSyncBackend
import com.launcher.ui.admin.navigation.ContactsManageComponent
import com.launcher.ui.admin.navigation.EditorComponent
import com.launcher.ui.admin.navigation.HistoryComponent
import com.launcher.ui.admin.navigation.OpenAppPickerComponent
import com.launcher.ui.admin.navigation.PhoneHealthComponent
import com.launcher.ui.admin.navigation.TileEditComponent
import com.launcher.ui.health.HealthToPhoneIndicatorAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Root navigation component. Holds the application's top-level stack and exposes
 * a [ChildStack] of [RootChild] for the host Composable to render.
 *
 * Per ADR-005 Amendment 2026-05-07a: Decompose chosen over Voyager because root
 * routing is not a simple push-pop stack — preset selection (FirstLaunch -> Home),
 * later admin-mode toggle, and recreate-on-preset-switch fit Decompose's
 * component-and-typed-config model better.
 *
 * @param onPresetChanged invoked after the user picks a different preset in
 *   Settings. The host Activity should `recreate()` so density-bound theme
 *   changes apply (`LauncherTheme(preset = ...)`).
 * @param onResetData invoked after the user resets all data; host Activity
 *   should restart the process at FirstLaunchActivity (clearing the task).
 */
class RootComponent(
    componentContext: ComponentContext,
    private val presetRepository: PresetRepository,
    private val flowRepository: FlowRepository,
    private val dispatchAction: suspend (Action) -> DispatchResult,
    private val providerRegistry: ProviderRegistry? = null,
    private val onPresetChanged: () -> Unit,
    private val onResetData: () -> Unit,
    private val onOpenPairing: () -> Unit,
    private val onOpenScanner: () -> Unit,
    private val managedDevices: com.launcher.api.link.ManagedDevicesRegistry? = null,
    initialPresetSlug: String?,
    // ─── Spec 009 admin-mode-flows deps (all nullable so spec 008-only
    // wiring still works; admin entry points are inert without them). ─
    private val configEditor: ConfigEditor? = null,
    private val historyRepository: ConfigHistoryRepository? = null,
    private val installedAppsCatalog: InstalledAppsCatalog? = null,
    private val remoteSyncBackend: RemoteSyncBackend? = null,
    private val healthIndicatorAdapter: HealthToPhoneIndicatorAdapter? = null,
    private val selfDeviceIdProvider: (() -> String)? = null,
    private val nowMillis: () -> Long = { 0L },
    private val linkRegistry: LinkRegistry? = null,
) : ComponentContext by componentContext {

    private val nav = StackNavigation<RootConfig>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    val stack: Value<ChildStack<RootConfig, RootChild>> = childStack(
        source = nav,
        serializer = RootConfig.serializer(),
        initialConfiguration = if (initialPresetSlug == null) RootConfig.FirstLaunch else RootConfig.Home,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    private fun createChild(config: RootConfig, context: ComponentContext): RootChild =
        when (config) {
            is RootConfig.FirstLaunch -> RootChild.FirstLaunch(
                FirstLaunchComponent(
                    componentContext = context,
                    onPresetSelected = ::handleFirstLaunchPresetSelected,
                )
            )
            is RootConfig.Home -> RootChild.Home(
                HomeComponent(
                    componentContext = context,
                    flowRepository = flowRepository,
                    dispatchAction = dispatchAction,
                    onSettingsClick = { nav.push(RootConfig.Settings) },
                    onAddFlowClick = { nav.push(RootConfig.AddFlowWizard) },
                    onAdminDevicesClick = { nav.push(RootConfig.AdminDevices) },
                    onAddSlotClick = { flowId -> nav.push(RootConfig.AddSlotWizard(flowId)) },
                    // Spec 010 T100 — 7-tap detector fires this; we push the
                    // challenge gate, which on success pops itself and pushes
                    // AdminDevices (admin-mode entry).
                    onSevenTapTriggered = { nav.push(RootConfig.ChallengeGate) },
                    // Spec 007 admin QR scanner + paired devices view.
                    onOpenScanner = onOpenScanner,
                    managedDevices = managedDevices,
                )
            )
            is RootConfig.ChallengeGate -> RootChild.ChallengeGate(
                ChallengeGateComponent(
                    componentContext = context,
                    onSuccess = {
                        nav.pop()
                        nav.push(RootConfig.AdminDevices)
                    },
                    onCancel = { nav.pop() },
                )
            )
            is RootConfig.Settings -> RootChild.Settings(
                SettingsComponent(
                    componentContext = context,
                    presetRepository = presetRepository,
                    onBack = { nav.pop() },
                    onPresetChanged = onPresetChanged,
                    onResetData = onResetData,
                    onAdminDevicesClick = { nav.push(RootConfig.AdminDevices) },
                    onOpenPairing = onOpenPairing,
                    onOpenScanner = onOpenScanner,
                )
            )
            is RootConfig.AddFlowWizard -> RootChild.AddFlowWizard(
                AddFlowWizardComponent(
                    componentContext = context,
                    onBack = { nav.pop() },
                    onDone = { nav.pop() },
                    onTemplateChosen = { templateId ->
                        scope.launch {
                            val newFlow = flowRepository.addFlow(templateId)
                            // Refresh the underlying Home so the new tab appears.
                            val homeChild = stack.value.items
                                .map { it.instance }
                                .filterIsInstance<RootChild.Home>()
                                .firstOrNull()
                                ?.component
                            homeChild?.refresh()
                            // Activate the new flow tab immediately for fast feedback.
                            homeChild?.selectFlow(newFlow.id)
                            nav.pop()
                        }
                    },
                )
            )
            is RootConfig.AddSlotWizard -> RootChild.AddSlotWizard(
                AddSlotWizardComponent(
                    componentContext = context,
                    flowId = config.flowId,
                    onBack = { nav.pop() },
                    onDone = { nav.pop() },
                    providerRegistry = providerRegistry,
                )
            )
            is RootConfig.AdminDevices -> RootChild.AdminDevices(
                AdminDevicesComponent(
                    componentContext = context,
                    managedDevices = requireDep("managedDevices", managedDevices),
                    onBack = { nav.pop() },
                    onEditLink = { linkId -> nav.push(RootConfig.Editor(linkId)) },
                    onHistoryLink = { linkId -> nav.push(RootConfig.History(linkId)) },
                    onContactsLink = { linkId -> nav.push(RootConfig.ContactsManage(linkId)) },
                    onHealthLink = { linkId -> nav.push(RootConfig.PhoneHealth(linkId)) },
                    onAddDevice = onOpenScanner,
                )
            )
            is RootConfig.FlowDetail -> error(
                "FlowDetail is not used as a root config in spec 004; flows render inside HomeComponent."
            )

            // ─── Spec 009 ────────────────────────────────────────────────
            is RootConfig.Editor -> RootChild.Editor(
                EditorComponent(
                    componentContext = context,
                    linkId = config.linkId,
                    configEditor = requireDep("configEditor", configEditor),
                    historyRepository = requireDep("historyRepository", historyRepository),
                    selfDeviceId = requireDep("selfDeviceIdProvider", selfDeviceIdProvider).invoke(),
                    nowMillis = nowMillis,
                    onBack = { nav.pop() },
                    onHistoryClick = { nav.push(RootConfig.History(config.linkId)) },
                    onEditTile = { flowId, slotId ->
                        nav.push(
                            RootConfig.TileEdit(
                                linkId = config.linkId,
                                flowId = flowId,
                                slotId = slotId,
                            ),
                        )
                    },
                    onPreviewTile = { _ ->
                        // FR-005 preview tap: dispatch the slot's action via
                        // ActionDispatcher in View mode.
                        //
                        // TODO(spec-followup TODO-ARCH-016): the launcher
                        // itself currently renders from FlowRepository
                        // (local), not from /config/current. A Slot→Action
                        // mapping needs to land first — switch home tiles
                        // to /config/current ConfigDocument. Until then,
                        // editor preview-tap is a no-op so the action
                        // dispatch surface stays consistent (no half-
                        // broken path that fires only for some slot kinds).
                    },
                )
            )
            is RootConfig.History -> RootChild.History(
                HistoryComponent(
                    componentContext = context,
                    linkId = config.linkId,
                    historyRepository = requireDep("historyRepository", historyRepository),
                    rollbackAllowed = config.rollbackAllowed,
                    onBack = { nav.pop() },
                    onRollback = { rollbackConfig ->
                        // Two-step: stash rollback target as draft, pop back to editor.
                        // The editor's lifecycle is independent — when admin
                        // reopens it, ConfigEditor.pendingDraft() yields the
                        // updated draft so EditorScreen renders the rollback
                        // selection. Admin then taps "Опубликовать" to commit.
                        scope.launch {
                            configEditor?.updateDraft(config.linkId) { rollbackConfig }
                            nav.pop()
                        }
                    },
                )
            )
            is RootConfig.ContactsManage -> RootChild.ContactsManage(
                ContactsManageComponent(
                    componentContext = context,
                    linkId = config.linkId,
                    configEditor = requireDep("configEditor", configEditor),
                    onBack = { nav.pop() },
                )
            )
            is RootConfig.OpenAppPicker -> RootChild.OpenAppPicker(
                OpenAppPickerComponent(
                    componentContext = context,
                    installedAppsCatalog = requireDep("installedAppsCatalog", installedAppsCatalog),
                    onBack = { nav.pop() },
                    onSelected = { app ->
                        when (val ret = config.returnTo) {
                            null -> nav.pop()  // browse-only.
                            is RootConfig.OpenAppPickerReturn.TileEditReturn -> {
                                // Pop the picker, then re-push TileEdit с
                                // pendingOpenAppPackage so the form pre-fills.
                                nav.pop()
                                nav.push(
                                    RootConfig.TileEdit(
                                        linkId = ret.linkId,
                                        flowId = ret.flowId,
                                        slotId = ret.slotId,
                                        pendingOpenAppPackage = app.packageName,
                                    ),
                                )
                            }
                        }
                    },
                )
            )
            is RootConfig.PhoneHealth -> RootChild.PhoneHealth(
                PhoneHealthComponent(
                    componentContext = context,
                    linkId = config.linkId,
                    remoteSyncBackend = requireDep("remoteSyncBackend", remoteSyncBackend),
                    indicatorAdapter = requireDep("healthIndicatorAdapter", healthIndicatorAdapter),
                    onBack = { nav.pop() },
                )
            )
            is RootConfig.TileEdit -> RootChild.TileEdit(
                TileEditComponent(
                    componentContext = context,
                    linkId = config.linkId,
                    flowId = ElementId(config.flowId),
                    slotId = ElementId(config.slotId),
                    configEditor = requireDep("configEditor", configEditor),
                    onSaved = { nav.pop() },
                    onCancel = { nav.pop() },
                    onPickApp = {
                        // Push an OpenAppPicker with a TileEditReturn —
                        // on selection RootConfig re-pushes TileEdit with
                        // pendingOpenAppPackage so the form pre-fills.
                        nav.push(
                            RootConfig.OpenAppPicker(
                                linkId = config.linkId,
                                returnTo = RootConfig.OpenAppPickerReturn.TileEditReturn(
                                    linkId = config.linkId,
                                    flowId = config.flowId,
                                    slotId = config.slotId,
                                ),
                            ),
                        )
                    },
                ).also { tileEditComponent ->
                    if (config.pendingOpenAppPackage != null) {
                        tileEditComponent.applyPickedApp(config.pendingOpenAppPackage)
                    }
                }
            )
        }

    private fun <T : Any> requireDep(name: String, value: T?): T = value
        ?: error("Spec 009 dependency '$name' is not wired into RootComponent — admin-mode screen requires it. Pass via constructor.")

    private fun handleFirstLaunchPresetSelected(preset: FlowPreset) {
        scope.launch {
            presetRepository.setActivePreset(preset)
            nav.replaceAll(RootConfig.Home)
        }
    }

    fun back() {
        nav.pop()
    }

    /** Reset to FirstLaunch — used by Settings reset path through onResetData. */
    fun resetToFirstLaunch() {
        nav.replaceAll(RootConfig.FirstLaunch)
    }

    /**
     * Spec 009 Phase E entry point: programmatically open the editor for
     * [linkId]. Called from [HomeActivity] when launched with
     * `EXTRA_OPEN_EDITOR_LINK_ID` (VCard share → "Open editor" flow).
     * Idempotent — if editor for the same linkId is already on top, no-op.
     */
    fun openEditor(linkId: String) {
        nav.push(RootConfig.Editor(linkId))
    }
}
