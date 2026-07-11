package com.launcher.preset.model

import kotlinx.serialization.Serializable

@Serializable
data class ComponentDeclaration(
    val id: String,
    val component: Component,
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,
    val descriptionKey: String? = null,
)

@Serializable
data class Pool(
    val schemaVersion: Int = 1,
    val declarations: List<ComponentDeclaration>,
) {
    fun byId(id: String): ComponentDeclaration? =
        declarations.firstOrNull { it.id == id }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}
