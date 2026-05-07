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
import com.launcher.api.ActionRequest
import com.launcher.api.DispatchResult
import com.launcher.api.FlowPreset
import com.launcher.api.FlowRepository
import com.launcher.api.PresetRepository
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
    private val dispatchAction: (ActionRequest) -> DispatchResult,
    private val onPresetChanged: () -> Unit,
    private val onResetData: () -> Unit,
    initialPresetSlug: String?,
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
                )
            )
            is RootConfig.Settings -> RootChild.Settings(
                SettingsComponent(
                    componentContext = context,
                    presetRepository = presetRepository,
                    onBack = { nav.pop() },
                    onPresetChanged = onPresetChanged,
                    onResetData = onResetData,
                )
            )
            is RootConfig.AddFlowWizard -> RootChild.AddFlowWizard(
                AddFlowWizardComponent(
                    componentContext = context,
                    onBack = { nav.pop() },
                    onDone = { nav.pop() },
                )
            )
            is RootConfig.AddSlotWizard -> RootChild.AddSlotWizard(
                AddSlotWizardComponent(
                    componentContext = context,
                    flowId = config.flowId,
                    onBack = { nav.pop() },
                    onDone = { nav.pop() },
                )
            )
            is RootConfig.AdminDevices -> RootChild.AdminDevices(
                AdminDevicesComponent(
                    componentContext = context,
                    onBack = { nav.pop() },
                )
            )
            is RootConfig.FlowDetail -> error(
                "FlowDetail is not used as a root config in spec 004; flows render inside HomeComponent."
            )
        }

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
}
