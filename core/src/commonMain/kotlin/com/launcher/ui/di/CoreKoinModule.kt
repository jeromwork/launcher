package com.launcher.ui.di

import org.koin.dsl.module

/**
 * Pure-Kotlin core registrations that work on every platform.
 *
 * Heavy facade classes (LauncherCore, ActionDispatcher, AppIndex, MockFlowRepository) still
 * live in `androidMain` because they touch Android APIs. They are registered in the
 * platform module ([androidPlatformModule] / iosPlatformModule). When those classes are
 * later refactored through expect/actual, registrations migrate here.
 */
val coreCommonModule = module {
    // Empty for now; populated when commonMain gains classes that need DI.
}
