package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.launcher.api.FlowPreset

/**
 * Decompose component for the first-launch picker. Carries the preset-selection
 * callback that ultimately persists the choice and advances the root stack.
 */
class FirstLaunchComponent(
    componentContext: ComponentContext,
    val onPresetSelected: (FlowPreset) -> Unit,
) : ComponentContext by componentContext
