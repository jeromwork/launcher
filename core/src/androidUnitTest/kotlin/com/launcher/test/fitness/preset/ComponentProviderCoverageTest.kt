package com.launcher.test.fitness.preset

import com.launcher.preset.adapter.NoOpProvider
import com.launcher.preset.fakes.FakeProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.ShapeStyle
import com.launcher.preset.model.TypographyScale
import com.launcher.preset.port.DefaultProviderRegistry
import com.launcher.preset.port.Provider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TASK-136 T136-033 (SC-006, FR-015a) — Component coverage fitness. Reworks the
 * TASK-120 fitness #3 for the canonical-ECS closed set:
 *
 *  1. Every `Component` subtype has a **working serializer** (encode → decode → equal).
 *  2. Every **behavioural** subtype resolves to a non-NoOp Provider in a DI graph.
 *
 * Exempt from the Provider requirement — they have nothing to "apply" to the OS:
 *  - **structural** subtypes ([Component.Workspace] / [Component.Flow] /
 *    [Component.ToolbarButton]) — pure screen-layout data;
 *  - the **state** component [LifecycleState] — it IS the apply-state, transitioned
 *    by the System (`ReconcileEngine`), never applied by a Provider.
 *
 * Orphan subtype (added but neither given a Provider nor exempted) → NoOp → fail.
 */
class ComponentProviderCoverageTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** One instance of every Component subtype in the closed set (11 data + state). */
    private val allComponents: List<Component> = listOf(
        Component.AppTile("com.example", "l.k"),
        Component.FontSize(1.0f),
        Component.Sos(),
        Component.Toolbar(emptyList(), "l.k"),
        Component.LauncherRole,
        Component.Theme(
            paletteSeedHex = "#000000",
            typographyScale = TypographyScale.Medium,
            shapeStyle = ShapeStyle.Rounded,
            darkMode = false,
        ),
        Component.Language(locale = "system"),
        Component.StatusBarPolicy,
        Component.Workspace(),
        Component.Flow(titleKey = "t"),
        Component.ToolbarButton(targetFlowId = "f", labelKey = "l"),
        LifecycleState.Pending,
        LifecycleState.Applied,
        LifecycleState.Skipped,
        LifecycleState.Unverifiable,
    )

    @Test
    fun everyComponentSubtype_hasWorkingSerializer() {
        for (c in allComponents) {
            val encoded = json.encodeToString(Component.serializer(), c)
            val decoded = json.decodeFromString(Component.serializer(), encoded)
            assertEquals("Component $c did not round-trip", c, decoded)
        }
    }

    private val exemptFromProvider = setOf(
        Component.Workspace::class,
        Component.Flow::class,
        Component.ToolbarButton::class,
        // The state component — no OS effector; transitioned by the System.
        LifecycleState::class,
        LifecycleState.Pending::class,
        LifecycleState.Applied::class,
        LifecycleState.Skipped::class,
        LifecycleState.Unverifiable::class,
        LifecycleState.Failed::class,
    )

    @Test
    fun everyBehaviouralComponentSubtype_hasNonNoOpProvider() {
        val handlers: Map<HandlerKey, Provider<out Component>> = mapOf(
            HandlerKey(Component.AppTile::class) to FakeProvider<Component.AppTile>(),
            HandlerKey(Component.FontSize::class) to FakeProvider<Component.FontSize>(),
            HandlerKey(Component.Sos::class) to FakeProvider<Component.Sos>(),
            HandlerKey(Component.Toolbar::class) to FakeProvider<Component.Toolbar>(),
            HandlerKey(Component.LauncherRole::class) to FakeProvider<Component.LauncherRole>(),
            HandlerKey(Component.Theme::class) to FakeProvider<Component.Theme>(),
            HandlerKey(Component.Language::class) to FakeProvider<Component.Language>(),
            HandlerKey(Component.StatusBarPolicy::class) to FakeProvider<Component.StatusBarPolicy>(),
        )
        val registry = DefaultProviderRegistry(handlers)

        for (instance in allComponents) {
            if (instance::class in exemptFromProvider) continue
            val resolved = registry.resolve(instance)
            assertNotEquals(
                "Fitness: ${instance::class} resolved to NoOpProvider. Missing Provider binding " +
                    "for this behavioural Component subtype (or add it to exemptFromProvider if it " +
                    "is structural / state data with nothing to apply).",
                NoOpProvider,
                resolved,
            )
        }
    }
}
