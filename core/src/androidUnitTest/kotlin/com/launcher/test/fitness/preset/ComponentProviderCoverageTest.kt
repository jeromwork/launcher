package com.launcher.test.fitness.preset

import com.launcher.preset.adapter.NoOpProvider
import com.launcher.preset.fakes.FakeProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.port.DefaultProviderRegistry
import com.launcher.preset.port.Provider
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * TASK-120 fitness #3 — every sealed subtype of Component must resolve to a
 * non-NoOp Provider in a test DI graph.
 *
 * Method: iterate `Component::class.sealedSubclasses`, build a DI graph with
 * FakeProvider bindings for the 4 MVP subtypes, assert each resolves to a
 * non-NoOp Provider. Orphan subtype (added but not bound) → NoOp → test fails.
 */
class ComponentProviderCoverageTest {

    @Test
    fun everyComponentSubclass_hasNonNoOpProvider() {
        val handlers: Map<HandlerKey, Provider<out Component>> = mapOf(
            HandlerKey(Component.AppTile::class) to FakeProvider<Component.AppTile>(),
            HandlerKey(Component.FontSize::class) to FakeProvider<Component.FontSize>(),
            HandlerKey(Component.Sos::class) to FakeProvider<Component.Sos>(),
            HandlerKey(Component.Toolbar::class) to FakeProvider<Component.Toolbar>(),
        )
        val registry = DefaultProviderRegistry(handlers)

        val samples = mapOf(
            Component.AppTile::class to Component.AppTile("com.example", "l.k"),
            Component.FontSize::class to Component.FontSize(1.0f),
            Component.Sos::class to Component.Sos(),
            Component.Toolbar::class to Component.Toolbar(emptyList(), "l.k"),
        )

        val subclasses = Component::class.sealedSubclasses
        for (klass in subclasses) {
            val instance = samples[klass]
                ?: error("Fitness #3: orphan Component subclass $klass has no sample instance. Add it to samples map when adding a new subtype.")
            val resolved = registry.resolve(instance)
            assertNotEquals(
                "Fitness #3: subclass $klass resolved to NoOpProvider. Missing Provider binding for this Component subtype.",
                NoOpProvider,
                resolved,
            )
        }
    }
}
