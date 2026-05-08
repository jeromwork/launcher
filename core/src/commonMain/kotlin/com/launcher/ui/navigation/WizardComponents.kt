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
 * AdminDevices placeholder component (spec 003 §Phase 7 mock).
 * Real paired-device list arrives in spec 009 (admin-mode-flows).
 */
class AdminDevicesComponent(
    componentContext: ComponentContext,
    val onBack: () -> Unit,
) : ComponentContext by componentContext
