package com.launcher.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.launcher.ui.navigation.RootChild
import com.launcher.ui.navigation.RootComponent
import com.launcher.ui.screens.AddFlowWizardScreen
import com.launcher.ui.screens.AddSlotWizardScreen
import com.launcher.ui.screens.AdminDevicesScreen
import com.launcher.ui.screens.FirstLaunchScreen
import com.launcher.ui.screens.HomeScreen
import com.launcher.ui.screens.PresetUiModel
import com.launcher.ui.screens.SettingsScreen

/**
 * Renders the root [RootComponent]'s child stack. Each [RootChild] picks its
 * Composable.
 *
 * @param presetUiModels already-localized UI models for the FirstLaunch picker.
 *   Caller (Activity / iOS app entry) resolves localized strings; this avoids
 *   pulling Android resource lookup into commonMain.
 */
@Composable
fun RootContent(
    component: RootComponent,
    presetUiModels: List<PresetUiModel>,
    modifier: Modifier = Modifier,
) {
    Children(stack = component.stack, modifier = modifier.fillMaxSize()) { childCreated ->
        when (val child = childCreated.instance) {
            is RootChild.FirstLaunch -> FirstLaunchScreen(
                presets = presetUiModels,
                onPresetSelected = child.component.onPresetSelected,
            )
            is RootChild.Home -> HomeScreen(component = child.component)
            is RootChild.Settings -> SettingsScreen(component = child.component)
            is RootChild.AddFlowWizard -> AddFlowWizardScreen(component = child.component)
            is RootChild.AddSlotWizard -> AddSlotWizardScreen(component = child.component)
            is RootChild.AdminDevices -> AdminDevicesScreen(component = child.component)
        }
    }
}
