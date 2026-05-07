package com.launcher.ui.navigation

/**
 * Sealed family of children produced by [RootComponent]'s child stack.
 * Each variant corresponds to one [RootConfig].
 *
 * Concrete child components are added incrementally as screens are migrated:
 * - T406 — [FirstLaunch]
 * - T407 — Home, FlowDetail
 * - T409 — Settings, AddFlowWizard, AddSlotWizard, AdminDevices
 *
 * The placeholder variant carries the [RootConfig] for screens not yet migrated
 * so the stack still renders something during the migration.
 */
sealed interface RootChild {

    data class FirstLaunch(val component: FirstLaunchComponent) : RootChild

    /** Stand-in for screens not yet migrated to Compose. Replaced per child as T407-T409 land. */
    data class Placeholder(val config: RootConfig) : RootChild
}
