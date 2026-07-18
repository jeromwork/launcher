package com.launcher.preset.engine

import com.launcher.preset.ecs.get
import com.launcher.preset.fakes.FakeProfileStore
import com.launcher.preset.fakes.FakeProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.model.RunMode
import com.launcher.preset.model.Tag
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.port.DefaultProviderRegistry
import com.launcher.preset.port.Provider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T127-018 (FR-014, SC-011) — honest state for settings the OS cannot read back.
 *
 * Android exposes no query for "is the status bar hidden?" — it is a chain of
 * intents through system settings. Recording `Applied` there would be a lie the
 * rest of the engine then trusts.
 */
class ReconcileEngineUnverifiableTest {

    private fun statusBarEntity(state: LifecycleState = LifecycleState.Pending) = Entity(
        id = "statusbar",
        components = listOf(Component.StatusBarPolicy, state),
        tags = setOf(Tag.System),
        wizardBehavior = WizardBehavior.AutoApply,
        critical = true,
    )

    private fun profileWith(vararg entities: Entity) = Profile(
        basedOnPreset = "p",
        presetVersion = 2,
        layoutKey = "grid",
        entities = entities.toList(),
    )

    private fun registryReturning(outcome: Outcome, onCheck: () -> Unit = {}): DefaultProviderRegistry {
        val provider: Provider<out Component> = FakeProvider<Component.StatusBarPolicy>(
            checkOutcome = { onCheck(); outcome },
            applyOutcome = { outcome },
        )
        return DefaultProviderRegistry(mapOf(HandlerKey(Component.StatusBarPolicy::class) to provider))
    }

    @Test
    fun apply_needsUserConfirmation_recordsUnverifiable_neverApplied() = runTest {
        val store = FakeProfileStore(profileWith(statusBarEntity()))
        val engine = ReconcileEngine(registryReturning(Outcome.NeedsUserConfirmation), store)

        val result = engine.run(RunMode.Single, targetId = "statusbar")

        val state = result.entities.single { it.id == "statusbar" }.get<LifecycleState>()
        assertEquals(LifecycleState.Unverifiable, state)
    }

    @Test
    fun bootCheck_skipsUnverifiableEntities_providerNotInvoked() = runTest {
        var checkCalls = 0
        val store = FakeProfileStore(profileWith(statusBarEntity(LifecycleState.Unverifiable)))
        val engine = ReconcileEngine(
            registryReturning(Outcome.NeedsApply, onCheck = { checkCalls++ }),
            store,
        )

        val result = engine.run(RunMode.BootCheck)

        // Re-checking on every cold start would nag the elderly user forever.
        assertEquals(0, checkCalls, "BootCheck must not re-probe an Unverifiable setting")
        assertEquals(
            LifecycleState.Unverifiable,
            result.entities.single { it.id == "statusbar" }.get<LifecycleState>(),
        )
    }

    @Test
    fun bootCheck_stillProbesOtherCriticalEntities() = runTest {
        var checkCalls = 0
        val store = FakeProfileStore(profileWith(statusBarEntity(LifecycleState.Applied)))
        val engine = ReconcileEngine(
            registryReturning(Outcome.Ok, onCheck = { checkCalls++ }),
            store,
        )

        engine.run(RunMode.BootCheck)

        assertTrue(checkCalls > 0, "BootCheck must still verify normal critical entities")
    }

    @Test
    fun single_reVerifiesUnverifiable_onExplicitSettingsAction() = runTest {
        val store = FakeProfileStore(profileWith(statusBarEntity(LifecycleState.Unverifiable)))
        val engine = ReconcileEngine(registryReturning(Outcome.Ok), store)

        val result = engine.run(RunMode.Single, targetId = "statusbar")

        // The user asked explicitly, and this time the provider could confirm.
        assertEquals(
            LifecycleState.Applied,
            result.entities.single { it.id == "statusbar" }.get<LifecycleState>(),
        )
    }
}
