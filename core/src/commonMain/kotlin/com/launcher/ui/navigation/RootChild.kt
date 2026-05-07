package com.launcher.ui.navigation

/**
 * Sealed family of children produced by [RootComponent]'s child stack.
 * Each variant corresponds to one [RootConfig].
 *
 * Concrete child components (FirstLaunchComponent, HomeComponent, etc.) are added
 * incrementally in tasks T406-T409 as their screens are migrated. Until then the
 * placeholder variant carries the [RootConfig] for the corresponding screen so the
 * stack still renders something during the migration.
 */
sealed interface RootChild {

    /**
     * Stand-in used while a screen has not yet been migrated to Compose. The host
     * renders a minimal "screen placeholder" Composable so debug builds don't crash.
     * Replaced per child as T406-T409 land.
     */
    data class Placeholder(val config: RootConfig) : RootChild
}
