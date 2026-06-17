package com.launcher.api.wizard.data

import kotlinx.serialization.Serializable

@Serializable
data class UICustomizationPoolBody(
    val platform: String,
    val options: List<UIOptionEntry>,
)

@Serializable
data class UIOptionEntry(
    val id: String,
    val kind: WireUIOptionKind,
    val questionKey: String,
    val descriptionKey: String? = null,
    val criticality: WireCriticality,
    val defaultValue: String,
    val choices: List<Choice>? = null,
    val choicesFrom: ChoicesFromRef? = null,
)

@Serializable
enum class WireUIOptionKind {
    @kotlinx.serialization.SerialName("simple-choice")
    SimpleChoice,

    @kotlinx.serialization.SerialName("pick-from-bundled")
    PickFromBundled,
}

@Serializable
data class Choice(val value: String, val labelKey: String)

@Serializable
data class ChoicesFromRef(
    val kind: WireConfigKind,
    val filter: String? = null,
)

@Serializable
enum class WireConfigKind {
    @kotlinx.serialization.SerialName("wizard.manifest")
    WizardManifest,

    @kotlinx.serialization.SerialName("screen.layout")
    ScreenLayout,

    @kotlinx.serialization.SerialName("tile.set")
    TileSet,

    @kotlinx.serialization.SerialName("system-settings.pool")
    SystemSettingsPool,

    @kotlinx.serialization.SerialName("ui-customization.pool")
    UICustomizationPool,
}
