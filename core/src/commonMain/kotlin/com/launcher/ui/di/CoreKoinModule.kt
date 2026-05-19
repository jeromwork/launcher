package com.launcher.ui.di

import org.koin.dsl.module

/**
 * Pure-Kotlin core registrations that work on every platform.
 *
 * Heavy facade classes (LauncherCore, ActionDispatcher, AppIndex) still live
 * in `androidMain` because they touch Android APIs. They are registered in the
 * platform module ([androidPlatformModule] / iosPlatformModule). When those
 * classes are later refactored through expect/actual, registrations migrate here.
 *
 * Spec 010 ARCH-016 closure (2026-05-19): `MockFlowRepository` is deleted —
 * the production [com.launcher.api.FlowRepository] binding is
 * [com.launcher.adapters.config.ConfigBackedFlowRepository], wired by each
 * backend flavor's `BackendInit.kt` over [com.launcher.api.config.ConfigEditor]
 * + [com.launcher.api.link.LinkRegistry].
 */
val coreCommonModule = module {
    // Empty for now; populated when commonMain gains classes that need DI.
}
