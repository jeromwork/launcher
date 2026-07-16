package com.launcher.preset.engine

import com.launcher.preset.fakes.FakeProfileStore
import com.launcher.preset.fakes.FakeProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Entity
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.model.RunMode
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

    private fun statusBarEntity(status: ComponentStatus = ComponentStatus.Pending) = Entity(
        id = "statusbar",
        component = Component.StatusBarPolicy(),
        wizardBehavior = WizardBehavior.AutoApply,
        critical = true,
        status = status,
    )

    private fun profileWith(vararg entities: Entity) = Profile(
        basedOnPreset = "p",
        presetVersion = 2,
        layoutKey = "grid",
        components = entities.toList(),
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

        val status = result.components.single { it.id == "statusbar" }.status
        assertEquals(ComponentStatus.Unverifiable, status)
    }

    @Test
    fun bootCheck_skipsUnverifiableEntities_providerNotInvoked() = runTest {
        var checkCalls = 0
        val store = FakeProfileStore(profileWith(statusBarEntity(ComponentStatus.Unverifiable)))
        val engine = ReconcileEngine(
            registryReturning(Outcome.NeedsApply, onCheck = { checkCalls++ }),
            store,
        )

        val result = engine.run(RunMode.BootCheck)

        // Re-checking on every cold start would nag the elderly user forever.
        assertEquals(0, checkCalls, "BootCheck must not re-probe an Unverifiable setting")
        assertEquals(
            ComponentStatus.Unverifiable,
            result.components.single { it.id == "statusbar" }.status,
        )
    }

    @Test
    fun bootCheck_stillProbesOtherCriticalEntities() = runTest {
        var checkCalls = 0
        val store = FakeProfileStore(profileWith(statusBarEntity(ComponentStatus.Applied)))
        val engine = ReconcileEngine(
            registryReturning(Outcome.Ok, onCheck = { checkCalls++ }),
            store,
        )

        engine.run(RunMode.BootCheck)

        assertTrue(checkCalls > 0, "BootCheck must still verify normal critical entities")
    }

    @Test
    fun single_reVerifiesUnverifiable_onExplicitSettingsAction() = runTest {
        val store = FakeProfileStore(profileWith(statusBarEntity(ComponentStatus.Unverifiable)))
        val engine = ReconcileEngine(registryReturning(Outcome.Ok), store)

        val result = engine.run(RunMode.Single, targetId = "statusbar")

        // The user asked explicitly, and this time the provider could confirm.
        assertEquals(
            ComponentStatus.Applied,
            result.components.single { it.id == "statusbar" }.status,
        )
    }
}
