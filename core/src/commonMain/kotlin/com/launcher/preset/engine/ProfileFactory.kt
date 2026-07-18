package com.launcher.preset.engine

import com.launcher.preset.ecs.entity
import com.launcher.preset.ecs.get
import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Profile
import com.launcher.preset.model.ValidationError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Builds a [Profile] from a [Preset] + [Pool] — the ECS **"spawn"** (TASK-136).
 *
 * A preset entry declares an entity's component **set**: a bundle-ref that
 * expands into components (+ `paramsOverride` merge + `parentRef`), all flattened
 * into one free bag — no privileged "base vs extra" (CL-2). The bundle is
 * discarded after spawn (Bevy: "zero runtime significance after creation"). Each
 * spawned entity carries [LifecycleState.Pending] as its initial apply-state (the
 * only source of it — the preset no longer carries a `status`, T136-046).
 */
class ProfileFactory(
    private val json: Json = defaultJson,
) {

    fun create(preset: Preset, pool: Pool): Profile {
        val spawned = mutableListOf<Entity>()
        val unknown = mutableListOf<String>()
        val entries = if (preset.activeComponents.isNotEmpty()) {
            preset.activeComponents
        } else {
            // Fall back to wizardFlow ordering when preset has no explicit active list
            preset.wizardFlow.sortedBy { it.order }.map { wf ->
                ActiveComponentEntry(wf.poolRef, wf.paramsOverride)
            }
        }
        for (entry in entries) {
            val decl = pool.byId(entry.poolRef)
            if (decl == null) {
                unknown += entry.poolRef
                continue
            }
            // Bundle expands into a flat component set; paramsOverride merges into
            // each (unknown keys are dropped on decode, so only the matching
            // component changes — every bundled bundle holds one data component).
            val resolved = decl.components.map { applyOverride(it, entry.paramsOverride) }
            spawned += entity(decl.id) {
                resolved.forEach { component(it) }
                decl.tags.forEach { tag(it) }
                parent(entry.parentRef)
                wizardBehavior = decl.wizardBehavior
                critical = decl.critical
                // Initial apply-state — mirrors the old `status = Pending` default.
                component(LifecycleState.Pending)
            }
        }
        return Profile(
            basedOnPreset = preset.presetId,
            presetVersion = preset.version,
            layoutKey = preset.layoutKey,
            entities = spawned,
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
        val byId = profile.entities.associateBy { it.id }

        // 1. Every declared parent must exist.
        for (entity in profile.entities) {
            val parentId = entity.parentId ?: continue
            if (parentId !in byId) {
                errors += ValidationError.DanglingParentRef(entity.id, parentId)
            }
        }

        // 2. No cycles. Walk each chain with a visited set — terminates on any
        //    input, including self-parenting and mutual loops (NFR-005).
        for (entity in profile.entities) {
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
        val flowIds = profile.entities
            .filter { it.get<Component.Flow>() != null }
            .map { it.id }
            .toSet()
        for (entity in profile.entities) {
            val button = entity.get<Component.ToolbarButton>() ?: continue
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
