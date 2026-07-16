package com.launcher.preset.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ProfileState(
    val opaque: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class Entity(
    val id: String,
    val component: Component,
    val wizardBehavior: WizardBehavior,
    val critical: Boolean,
    val status: ComponentStatus = ComponentStatus.Pending,
)

@Serializable
data class Profile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val basedOnPreset: String,
    val presetVersion: Int,
    val layoutKey: String,
    val components: List<Entity> = emptyList(),
    val preWizardSnapshot: Profile? = null,
    val snapshotTimestamp: Long? = null,
    val unknownRefs: List<String> = emptyList(),
    val state: ProfileState = ProfileState(),
) {
    fun mark(id: String, status: ComponentStatus): Profile =
        copy(components = components.map { if (it.id == id) it.copy(status = status) else it })

    fun replaceComponent(id: String, newComponent: Component): Profile =
        copy(components = components.map { if (it.id == id) it.copy(component = newComponent) else it })

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}
