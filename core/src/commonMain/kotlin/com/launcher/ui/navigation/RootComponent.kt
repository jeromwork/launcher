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
 */
class RootComponent(
    componentContext: ComponentContext,
    private val presetRepository: PresetRepository,
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
                    onPresetSelected = ::handlePresetSelected,
                )
            )
            // Other screens fill in T407-T409.
            else -> RootChild.Placeholder(config)
        }

    private fun handlePresetSelected(preset: FlowPreset) {
        scope.launch {
            presetRepository.setActivePreset(preset)
            nav.replaceAll(RootConfig.Home)
        }
    }

    fun openFlow(flowId: String) {
        nav.push(RootConfig.FlowDetail(flowId))
    }

    fun openSettings() {
        nav.push(RootConfig.Settings)
    }

    fun openAddFlowWizard() {
        nav.push(RootConfig.AddFlowWizard)
    }

    fun openAddSlotWizard(flowId: String) {
        nav.push(RootConfig.AddSlotWizard(flowId))
    }

    fun openAdminDevices() {
        nav.push(RootConfig.AdminDevices)
    }

    fun back() {
        nav.pop()
    }

    /**
     * Reset to FirstLaunch (used by Settings -> "Сбросить данные"). Caller is
     * responsible for clearing the underlying PresetRepository and any other data.
     */
    fun resetToFirstLaunch() {
        nav.replaceAll(RootConfig.FirstLaunch)
    }
}
