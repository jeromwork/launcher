package com.launcher.app.di

import com.launcher.adapters.wizard.AndroidAnimationPreferenceProvider
import com.launcher.adapters.wizard.AndroidLocaleProvider
import com.launcher.adapters.wizard.AndroidStringResolver
import com.launcher.adapters.wizard.AndroidSystemSettingAdapter
import com.launcher.adapters.wizard.BundledConfigSource
import com.launcher.adapters.wizard.PersistentCheckpointStore
import com.launcher.adapters.wizard.PersistentDismissedHintsStore
import com.launcher.adapters.wizard.PersistentUserPreferencesStore
import com.launcher.adapters.wizard.SystemClock
import com.launcher.api.localization.LocaleProvider
import com.launcher.api.localization.StringResolver
import com.launcher.api.wizard.AnimationPreferenceProvider
import com.launcher.api.wizard.Clock
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.DiagnosticEmitter
import com.launcher.api.wizard.DismissedHintsStore
import com.launcher.api.wizard.PermissionRequestPort
import com.launcher.api.wizard.StepType
import com.launcher.api.wizard.SystemSettingPort
import com.launcher.api.wizard.UserPreferencesStore
import com.launcher.api.wizard.WizardCheckpointStore
import com.launcher.api.wizard.WizardEngine
import com.launcher.api.wizard.WizardStep
import com.launcher.app.wizard.NoopDiagnosticEmitter
import com.launcher.app.wizard.NoopPermissionRequestPort
import com.launcher.ui.wizard.TutorialHintManager
import com.launcher.ui.wizard.WizardEngineImpl
import com.launcher.ui.wizard.steps.StepHost
import com.launcher.ui.wizard.steps.SystemSettingStep
import com.launcher.ui.wizard.steps.TutorialHintStep
import com.launcher.ui.wizard.steps.UIChoiceStep
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Spec 015 (F-3) Koin wiring.
 *
 * Bindings:
 *  - Ports → real Android adapters.
 *  - Three [StepHost]s (one per step type) — UI binds to these for rendering.
 *  - Three [WizardStep] impls + Map<StepType, WizardStep> for the engine.
 *  - [WizardEngine] singleton.
 *  - [TutorialHintManager].
 *
 * No real [DiagnosticEmitter] in F-3 (per A-17) — bound to a no-op stub that
 * S-1+ replaces with an analytics emitter.
 *
 * [PermissionRequestPort] is bound to a no-op stub here for the DI graph;
 * `WizardActivity` registers the real ActivityResultLauncher-backed instance
 * on its own (per Android ActivityResult contract — must register before
 * onStart).
 */
val spec015Module = module {

    single<Clock> { SystemClock() }
    single<LocaleProvider> { AndroidLocaleProvider(androidContext()) }
    single<StringResolver> { AndroidStringResolver(androidContext()) }
    single<AnimationPreferenceProvider> { AndroidAnimationPreferenceProvider(androidContext()) }

    single<WizardCheckpointStore> { PersistentCheckpointStore(androidContext()) }
    single<DismissedHintsStore> { PersistentDismissedHintsStore(androidContext()) }
    single<UserPreferencesStore> { PersistentUserPreferencesStore(androidContext()) }

    single<ConfigSource> { BundledConfigSource(androidContext()) }

    single<DiagnosticEmitter> { NoopDiagnosticEmitter() }
    single<PermissionRequestPort> { NoopPermissionRequestPort() }

    single<SystemSettingPort> {
        AndroidSystemSettingAdapter(
            context = androidContext(),
            configSource = get(),
            permissionRequestPort = get(),
            userPreferencesStore = get(),
        )
    }

    // Three step hosts — UI binds to them for rendering, engine binds via Step impls.
    single<StepHost>(named("uiChoiceHost")) { StepHost() }
    single<StepHost>(named("systemSettingHost")) { StepHost() }
    single<StepHost>(named("tutorialHintHost")) { StepHost() }

    single { TutorialHintManager(get(), get(), get()) }

    single<Map<StepType, WizardStep>> {
        mapOf(
            StepType.UIChoice to UIChoiceStep(host = get(named("uiChoiceHost"))),
            StepType.SystemSetting to SystemSettingStep(
                host = get(named("systemSettingHost")),
                systemSettingPort = get(),
                userPreferencesStore = get(),
                diagnostics = get(),
                clock = get(),
            ),
            StepType.TutorialHint to TutorialHintStep(
                host = get(named("tutorialHintHost")),
                hintManager = get(),
            ),
        )
    }

    single<WizardEngine> {
        WizardEngineImpl(
            steps = get(),
            checkpointStore = get(),
            userPreferencesStore = get(),
            configSource = get(),
            clock = get(),
            diagnostics = get(),
        )
    }
}
