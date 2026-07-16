package com.launcher.preset.engine

import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.Component
import com.launcher.preset.model.Blueprint
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Entity
import com.launcher.preset.model.WizardBehavior
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Builds a [Profile] from a [Preset] + [Pool]. Resolves poolRefs, applies
 * paramsOverride via JSON merge, initializes statuses.
 */
class ProfileFactory(
    private val json: Json = defaultJson,
) {

    fun create(preset: Preset, pool: Pool): Profile {
        val components = mutableListOf<Entity>()
        val unknown = mutableListOf<String>()
        val entries = if (preset.activeComponents.isNotEmpty()) {
            preset.activeComponents
        } else {
            // Fall back to wizardFlow ordering when preset has no explicit active list
            preset.wizardFlow.sortedBy { it.order }.map { wf ->
                ActiveComponentEntry(wf.poolRef, wf.paramsOverride, ComponentStatus.Pending)
            }
        }
        for (entry in entries) {
            val decl = pool.byId(entry.poolRef)
            if (decl == null) {
                unknown += entry.poolRef
                continue
            }
            val resolvedComponent = applyOverride(decl.component, entry.paramsOverride)
            components += Entity(
                id = decl.id,
                component = resolvedComponent,
                wizardBehavior = decl.wizardBehavior,
                critical = decl.critical,
                status = entry.status,
            )
        }
        return Profile(
            basedOnPreset = preset.presetId,
            presetVersion = preset.version,
            layoutKey = preset.layoutKey,
            components = components,
            unknownRefs = unknown,
        )
    }

    private fun applyOverride(component: Component, override: JsonObject?): Component {
        if (override == null || override.isEmpty()) return component
        val encoded = json.encodeToJsonElement(Component.serializer(), component).jsonObject
        val merged = buildJsonObject {
            for ((k, v) in encoded) put(k, v)
            for ((k, v) in override) put(k, v)
        }
        return json.decodeFromJsonElement(Component.serializer(), merged)
    }

    companion object {
        private val defaultJson = Json {
            classDiscriminator = "type"
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
