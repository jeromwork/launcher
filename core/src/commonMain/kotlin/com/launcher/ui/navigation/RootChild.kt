package com.launcher.ui.navigation

/**
 * Sealed family of children produced by [RootComponent]'s child stack.
 * Each variant corresponds to one [RootConfig].
 *
 * Concrete child components are added incrementally as screens are migrated:
 * - T406 — [FirstLaunch]
 * - T407 — [Home]
 * - T408 — FlowScreen lives inside Home, not as a separate route
 * - T409 — Settings, AddFlowWizard, AddSlotWizard, AdminDevices
 */
sealed interface RootChild {

    data class FirstLaunch(val component: FirstLaunchComponent) : RootChild

    data class Home(val component: HomeComponent) : RootChild

    /** Stand-in for screens not yet migrated to Compose. Replaced per child as T409 lands. */
    data class Placeholder(val config: RootConfig) : RootChild
}
