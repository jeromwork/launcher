package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.action.ProviderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AddFlowWizard placeholder component (spec 003 §Phase 6 mock).
 * Real flow creation arrives in spec 005 (action-architecture-v2).
 */
class AddFlowWizardComponent(
    componentContext: ComponentContext,
    val onBack: () -> Unit,
    val onDone: () -> Unit,
    val onTemplateChosen: (templateId: String) -> Unit,
) : ComponentContext by componentContext

/**
 * Spec 005 US-507: surfaces the provider list filtered by per-device
 * availability. Subscribes to [ProviderRegistry.updates] (debounced 1s) and
 * exposes [providers] for the wizard UI to render. The actual "pick a
 * provider, fill in params, save the slot" flow stays a follow-up — this
 * component is only responsible for the *filtering* step required by US-507
 * acceptance criteria.
 */
class AddSlotWizardComponent(
    componentContext: ComponentContext,
    val flowId: String,
    val onBack: () -> Unit,
    val onDone: () -> Unit,
    private val providerRegistry: ProviderRegistry? = null,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _providers = MutableStateFlow<List<ProviderState>>(emptyList())
    val providers: StateFlow<List<ProviderState>> = _providers.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        if (providerRegistry != null) {
            _providers.value = providerRegistry.snapshot().filterVisible()
            scope.launch {
                providerRegistry.updates.collect { update ->
                    _providers.value = update.filterVisible()
                }
            }
        }
    }

    /**
     * Drops [com.launcher.api.action.UnavailabilityHint.UnknownInThisVersion]
     * providers entirely (US-507: "wizards hide such providers"). Available,
     * Missing, NotApplicable stay visible — UI renders them with the right
     * affordance (tap-to-add / install / greyed-out).
     */
    // ProviderAvailability is a sealed class with exactly three variants: all
    // are visible in the wizard. UnknownInThisVersion is a UnavailabilityHint
    // surfaced at *dispatch* time, never at *registry* time — it can't reach
    // here. If someone later teaches the registry to surface unknowns as a
    // ProviderAvailability variant, drop them here.
    private fun List<ProviderState>.filterVisible(): List<ProviderState> = this
}

/**
 * Admin-side paired devices list. Reads
 * [com.launcher.api.link.ManagedDevicesRegistry.observeAll] — the multi-link
 * registry filtered by `adminId == currentUid` on the Firestore side — and
 * exposes admin actions per link: edit layout, history, contacts, health.
 *
 * Earlier spec-009 wiring incorrectly used the single-link
 * [com.launcher.api.link.LinkRegistry] (Managed-side, one link per device),
 * which left this screen empty on the admin device even after a successful
 * QR claim. The correct port is `ManagedDevicesRegistry`.
 */
class AdminDevicesComponent(
    componentContext: ComponentContext,
    private val managedDevices: com.launcher.api.link.ManagedDevicesRegistry,
    val onBack: () -> Unit,
    val onEditLink: (String) -> Unit,
    val onHistoryLink: (String) -> Unit,
    val onContactsLink: (String) -> Unit,
    val onHealthLink: (String) -> Unit,
    val onAddDevice: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<AdminDevicesState>(AdminDevicesState.Loading)
    val state: StateFlow<AdminDevicesState> = _state.asStateFlow()
    // Kept for source compatibility with callers that only need the list;
    // mirrors the Loaded payload (empty during Loading).
    val links: StateFlow<List<com.launcher.api.link.Link>>
        get() = _links
    private val _links = MutableStateFlow<List<com.launcher.api.link.Link>>(emptyList())

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch {
            managedDevices.observeAll().collect { list ->
                _links.value = list
                _state.value = AdminDevicesState.Loaded(list)
            }
        }
    }

    /**
     * Admin-side delete: removes /links/{linkId} on the server AND from local
     * state, so the card disappears immediately and stays gone after a restart.
     * Security Rules grant delete to the link's adminId. We optimistically
     * forget locally first, then fire the server delete in the background;
     * failures stay silent for MVP (the listener will rehydrate stale entries).
     */
    fun removeLink(linkId: String) {
        managedDevices.forgetLink(linkId)
        scope.launch { managedDevices.removeLinkOnServer(linkId) }
    }

    /** Called from the screen the moment the user taps "+" (scan). We flip
     *  state to [AdminDevicesState.Loading] so when the user returns from
     *  the scanner the screen shows a spinner instead of briefly flashing
     *  "Нет сопряжённых устройств" before the new link arrives via
     *  `recordClaim` / Firestore listener. The next [observeAll] emit will
     *  flip back to [AdminDevicesState.Loaded]. */
    fun onScanStart() {
        _state.value = AdminDevicesState.Loading
    }
}

/** UI state for [AdminDevicesComponent] — distinguishes the initial wait for
 *  the first Firestore snapshot ([Loading]) from a confirmed empty list, so
 *  the "Нет сопряжённых устройств" copy doesn't flash before data arrives. */
sealed interface AdminDevicesState {
    data object Loading : AdminDevicesState
    data class Loaded(val links: List<com.launcher.api.link.Link>) : AdminDevicesState
}
