package com.launcher.preset.engine

import com.launcher.preset.fakes.FakeInteractionSink
import com.launcher.preset.fakes.FakeProfileStore
import com.launcher.preset.fakes.FakeProvider
import com.launcher.preset.model.ChangeItem
import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.RunMode
import com.launcher.preset.port.DefaultProviderRegistry
import com.launcher.preset.port.Provider
import com.launcher.preset.roundtrip.mvpPool
import com.launcher.preset.roundtrip.simpleLauncherPreset
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconcileEngineTest {

    private fun okAllHandlers(): Map<HandlerKey, Provider<out Component>> = mapOf(
        HandlerKey(Component.FontSize::class) to FakeProvider<Component.FontSize>(),
        HandlerKey(Component.AppTile::class) to FakeProvider<Component.AppTile>(),
        HandlerKey(Component.Sos::class) to FakeProvider<Component.Sos>(),
        HandlerKey(Component.Toolbar::class) to FakeProvider<Component.Toolbar>(),
    )

    @Test
    fun wizardMode_appliesEachComponent_reachesTerminalStatus() = runTest {
        val store = FakeProfileStore(ProfileFactory().create(simpleLauncherPreset(), mvpPool()))
        val engine = ReconcileEngine(DefaultProviderRegistry(okAllHandlers()), store)
        val result = engine.run(RunMode.Wizard, sink = FakeInteractionSink())
        assertTrue(result.components.all { it.status != ComponentStatus.Pending })
    }

    @Test
    fun wizardMode_interactiveSkipsWhenSinkReturnsNull() = runTest {
        val store = FakeProfileStore(ProfileFactory().create(simpleLauncherPreset(), mvpPool()))
        val engine = ReconcileEngine(DefaultProviderRegistry(okAllHandlers()), store)
        val sink = FakeInteractionSink(defaultAnswer = { null })
        val result = engine.run(RunMode.Wizard, sink = sink)
        val fontComponent = result.components.first { it.id == "font-tile" }
        assertEquals(ComponentStatus.Skipped, fontComponent.status)
    }

    @Test
    fun bootCheckMode_appliesOnlyCriticalComponents() = runTest {
        val store = FakeProfileStore(ProfileFactory().create(simpleLauncherPreset(), mvpPool()))
        val engine = ReconcileEngine(DefaultProviderRegistry(okAllHandlers()), store)
        val result = engine.run(RunMode.BootCheck)
        val sos = result.components.first { it.id == "sos-main" }
        val font = result.components.first { it.id == "font-tile" }
        assertEquals(ComponentStatus.Applied, sos.status)
        assertEquals(ComponentStatus.Pending, font.status)
    }

    @Test
    fun singleMode_appliesTargetOnly() = runTest {
        val store = FakeProfileStore(ProfileFactory().create(simpleLauncherPreset(), mvpPool()))
        val engine = ReconcileEngine(DefaultProviderRegistry(okAllHandlers()), store)
        val result = engine.run(RunMode.Single, targetId = "font-tile")
        val font = result.components.first { it.id == "font-tile" }
        assertEquals(ComponentStatus.Applied, font.status)
        val sos = result.components.first { it.id == "sos-main" }
        assertEquals(ComponentStatus.Pending, sos.status)
    }

    @Test
    fun remotePush_appliesAddedAndRemoved_andParamsChanged() = runTest {
        val store = FakeProfileStore(ProfileFactory().create(simpleLauncherPreset(), mvpPool()))
        val engine = ReconcileEngine(DefaultProviderRegistry(okAllHandlers()), store)
        val newAppTile = Component.AppTile(
            packageName = "org.telegram.messenger",
            labelKey = "pool.tile.telegram.label",
        )
        val changes = listOf(
            ChangeItem.Removed("toolbar-minimal"),
            ChangeItem.Added("tile-telegram", newAppTile),
            ChangeItem.ParamsChanged("font-tile", Component.FontSize(scale = 2.0f)),
        )
        val result = engine.run(RunMode.RemotePush, changes = changes)
        assertTrue(result.components.none { it.id == "toolbar-minimal" })
        val added = result.components.first { it.id == "tile-telegram" }
        assertEquals(ComponentStatus.Applied, added.status)
        val font = result.components.first { it.id == "font-tile" }
        assertEquals(2.0f, (font.component as Component.FontSize).scale)
    }

    @Test
    fun provider_failed_marksComponentFailed() = runTest {
        val store = FakeProfileStore(ProfileFactory().create(simpleLauncherPreset(), mvpPool()))
        val handlers = mapOf<HandlerKey, Provider<out Component>>(
            HandlerKey(Component.FontSize::class) to FakeProvider<Component.FontSize>(
                applyOutcome = { Outcome.Failed(com.launcher.preset.model.FailReason.NetworkUnavailable) }
            ),
            HandlerKey(Component.AppTile::class) to FakeProvider<Component.AppTile>(),
            HandlerKey(Component.Sos::class) to FakeProvider<Component.Sos>(),
            HandlerKey(Component.Toolbar::class) to FakeProvider<Component.Toolbar>(),
        )
        val engine = ReconcileEngine(DefaultProviderRegistry(handlers), store)
        val result = engine.run(RunMode.Wizard, sink = FakeInteractionSink())
        val font = result.components.first { it.id == "font-tile" }
        assertEquals(ComponentStatus.Failed, font.status)
    }
}
