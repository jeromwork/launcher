package com.launcher.preset.settings

import com.launcher.preset.ecs.get
import com.launcher.preset.engine.ReconcileEngine
import com.launcher.preset.model.Component
import com.launcher.preset.model.FailReason
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Profile
import com.launcher.preset.model.RunMode
import com.launcher.preset.port.ApplyResult
import com.launcher.preset.port.PresetSource
import com.launcher.preset.port.ProfileStore
import com.launcher.preset.port.SettingsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * TASK-69 (FR-008, FR-009, FR-010, FR-011) — today's real [SettingsGateway]:
 * wires [SettingsPresentationBuilder] (projection) + [ReconcileEngine] (apply)
 * + [ProfileStore] / [PresetSource] behind the port. The engine is swappable
 * without touching the ViewModel or screen (SEQ-1/SEQ-2 plan-level lifelines).
 */
class EngineSettingsGateway(
    private val engine: ReconcileEngine,
    private val profileStore: ProfileStore,
    private val presetSource: PresetSource,
    private val builder: SettingsPresentationBuilder = SettingsPresentationBuilder(),
) : SettingsGateway {

    override fun observe(): Flow<SettingsView> =
        profileStore.observe().map { profile -> projectOrEmpty(profile) }

    private suspend fun projectOrEmpty(profile: Profile?): SettingsView {
        if (profile == null) return SettingsView(sections = emptyList(), actions = emptyList())
        val settingsMap = presetSource.loadPreset(profile.basedOnPreset)?.settingsMap.orEmpty()
        return builder.build(profile, settingsMap)
    }

    override suspend fun apply(poolRef: String, params: JsonObject): ApplyResult {
        val before = profileStore.load()
            ?: return ApplyResult.Failed(FailReason.InternalError("settings.apply.no_profile"))
        val entity = before.entities.firstOrNull { it.id == poolRef }
            ?: return ApplyResult.Failed(FailReason.InternalError("settings.apply.unknown_ref"))
        val oldComponent = entity.components.firstOrNull { it !is LifecycleState }
            ?: return ApplyResult.Failed(FailReason.InternalError("settings.apply.no_component"))
        val newComponent = mergeParams(oldComponent, params)

        profileStore.save(before.with(poolRef, newComponent))
        val result = engine.run(mode = RunMode.Single, targetId = poolRef)
        val newState = result.entities.firstOrNull { it.id == poolRef }?.get<LifecycleState>()

        return if (newState is LifecycleState.Failed) {
            // FR-010: keep the prior value on failure; only the state records it.
            profileStore.save(before.with(poolRef, oldComponent).setState(poolRef, newState))
            ApplyResult.Failed(newState.reason)
        } else if (newState is LifecycleState.Unverifiable) {
            // FR-013: OS gave no read-back — honest "unknown", not a lie.
            ApplyResult.NeedsSystemDialog
        } else {
            ApplyResult.Applied
        }
    }

    /**
     * Type-agnostic field merge (same technique as [com.launcher.preset.engine.ProfileFactory
     * .applyOverride]) — encode the current component to JSON, overlay [params], decode back
     * through the polymorphic [Component] serializer. No `when(component)` needed (rule 1).
     */
    private fun mergeParams(component: Component, params: JsonObject): Component {
        if (params.isEmpty()) return component
        val encoded = json.encodeToJsonElement(Component.serializer(), component).jsonObject
        val merged = buildJsonObject {
            for ((k, v) in encoded) put(k, v)
            for ((k, v) in params) put(k, v)
        }
        return json.decodeFromJsonElement(Component.serializer(), merged)
    }

    companion object {
        private val json = Json {
            classDiscriminator = "type"
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
