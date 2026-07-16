package com.launcher.preset.engine

import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.Component
import com.launcher.preset.model.Blueprint
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Entity
import com.launcher.preset.model.ValidationError
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
                // T127-026 (FR-011): the preset entry declares the tree edge.
                parentId = entry.parentRef,
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

    /**
     * T127-019 (FR-016) — structural integrity of the assembled tree.
     *
     * Returns errors as values; the domain never throws (existing convention).
     * Runtime queries tolerate a broken tree (an orphan is simply never returned),
     * so this is the gate that turns "half a screen renders" into a named failure.
     *
     * Also reusable at authoring time — see TASK-132 (pre-share preset validation).
     */
    fun validateHierarchy(profile: Profile): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val byId = profile.components.associateBy { it.id }

        // 1. Every declared parent must exist.
        for (entity in profile.components) {
            val parentId = entity.parentId ?: continue
            if (parentId !in byId) {
                errors += ValidationError.DanglingParentRef(entity.id, parentId)
            }
        }

        // 2. No cycles. Walk each chain with a visited set — terminates on any
        //    input, including self-parenting and mutual loops (NFR-005).
        for (entity in profile.components) {
            val seen = mutableSetOf<String>()
            var cursor: Entity? = entity
            while (cursor != null) {
                if (!seen.add(cursor.id)) {
                    errors += ValidationError.CircularParentRef(seen.toList())
                    break
                }
                cursor = cursor.parentId?.let(byId::get)
            }
        }

        // 3. Every toolbar button must target an existing Flow entity.
        val flowIds = profile.components
            .filter { it.component is Component.Flow }
            .map { it.id }
            .toSet()
        for (entity in profile.components) {
            val button = entity.component as? Component.ToolbarButton ?: continue
            if (button.targetFlowId !in flowIds) {
                errors += ValidationError.DanglingTargetRef(entity.id, button.targetFlowId)
            }
        }

        return errors.distinct()
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
