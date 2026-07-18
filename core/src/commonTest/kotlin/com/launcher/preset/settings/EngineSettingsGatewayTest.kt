package com.launcher.preset.settings

import com.launcher.preset.ecs.get
import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.engine.ReconcileEngine
import com.launcher.preset.fakes.FakePresetSource
import com.launcher.preset.fakes.FakeProfileStore
import com.launcher.preset.fakes.FakeProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.FailReason
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Outcome
import com.launcher.preset.port.ApplyResult
import com.launcher.preset.port.DefaultProviderRegistry
import com.launcher.preset.roundtrip.mvpPool
import com.launcher.preset.roundtrip.simpleLauncherPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TASK-69 T069-013 (SC-008, FR-010) — [EngineSettingsGateway] contract test on
 * fakes: `apply()` drives `ReconcileEngine` -> `Provider.apply` and persists
 * through `ProfileStore`; a failed apply keeps the prior value.
 */
class EngineSettingsGatewayTest {

    private fun gateway(fontOutcome: Outcome): Triple<EngineSettingsGateway, FakeProfileStore, FakePresetSource> {
        val profile = ProfileFactory().create(simpleLauncherPreset(), mvpPool())
            .let { p -> p.entities.fold(p) { acc, e -> acc.setState(e.id, LifecycleState.Applied) } }
        val profileStore = FakeProfileStore(profile)
        val presetSource = FakePresetSource(mapOf("simple-launcher" to simpleLauncherPreset()))
        val registry = DefaultProviderRegistry(
            handlers = mapOf(
                HandlerKey(Component.FontSize::class) to FakeProvider<Component.FontSize>(applyOutcome = { fontOutcome }),
            ),
        )
        val engine = ReconcileEngine(registry = registry, store = profileStore)
        val gateway = EngineSettingsGateway(engine = engine, profileStore = profileStore, presetSource = presetSource)
        return Triple(gateway, profileStore, presetSource)
    }

    @Test
    fun `apply persists the new value and Applied state on success`() = runTest {
        val (gateway, profileStore, _) = gateway(fontOutcome = Outcome.Ok)

        val result = gateway.apply("font-tile", JsonObject(mapOf("scale" to JsonPrimitive(2.0f))))

        assertEquals(ApplyResult.Applied, result)
        val saved = profileStore.load()!!
        val entity = saved.entities.first { it.id == "font-tile" }
        assertEquals(2.0f, entity.get<Component.FontSize>()!!.scale)
        assertEquals(LifecycleState.Applied, entity.get<LifecycleState>())
    }

    @Test
    fun `apply keeps the prior value and records Failed state on failure`() = runTest {
        val (gateway, profileStore, _) = gateway(
            fontOutcome = Outcome.Failed(FailReason.NetworkUnavailable),
        )

        val result = gateway.apply("font-tile", JsonObject(mapOf("scale" to JsonPrimitive(2.0f))))

        assertIs<ApplyResult.Failed>(result)
        assertEquals(FailReason.NetworkUnavailable, result.reason)
        val saved = profileStore.load()!!
        val entity = saved.entities.first { it.id == "font-tile" }
        assertEquals(1.6f, entity.get<Component.FontSize>()!!.scale, "FR-010: prior value must survive a failed apply")
        assertEquals(LifecycleState.Failed(FailReason.NetworkUnavailable), entity.get<LifecycleState>())
    }

    @Test
    fun `apply on unresolved poolRef fails without touching the profile`() = runTest {
        val (gateway, profileStore, _) = gateway(fontOutcome = Outcome.Ok)
        val before = profileStore.load()

        val result = gateway.apply("does-not-exist", JsonObject(emptyMap()))

        assertIs<ApplyResult.Failed>(result)
        assertEquals(before, profileStore.load())
    }

    @Test
    fun `observe projects the active profile through the builder`() = runTest {
        val (gateway, _, _) = gateway(fontOutcome = Outcome.Ok)

        val view = gateway.observe().first()

        assertTrue(view.sections.isNotEmpty())
        assertTrue(view.sections.flatMap { it.rows }.any { it.poolRef == "font-tile" })
    }
}
