package com.launcher.ui.navigation

/**
 * Sealed family of children produced by [RootComponent]'s child stack.
 * Each variant corresponds to one [RootConfig].
 */
sealed interface RootChild {

    data class FirstLaunch(val component: FirstLaunchComponent) : RootChild

    data class Home(val component: HomeComponent) : RootChild

    data class Settings(val component: SettingsComponent) : RootChild

    data class AddFlowWizard(val component: AddFlowWizardComponent) : RootChild

    data class AddSlotWizard(val component: AddSlotWizardComponent) : RootChild

    data class AdminDevices(val component: AdminDevicesComponent) : RootChild
}
