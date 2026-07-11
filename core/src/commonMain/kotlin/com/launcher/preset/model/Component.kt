package com.launcher.preset.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Component {

    @Serializable
    @SerialName("AppTile")
    data class AppTile(
        val packageName: String,
        val labelKey: String,
        val iconKey: String? = null,
        val pinProtected: Boolean = false,
    ) : Component()

    @Serializable
    @SerialName("FontSize")
    data class FontSize(
        val scale: Float,
    ) : Component()

    @Serializable
    @SerialName("Sos")
    data class Sos(
        val shareLocation: Boolean = true,
        val autoAnswer: Boolean = true,
    ) : Component()

    @Serializable
    @SerialName("Toolbar")
    data class Toolbar(
        val items: List<String>,
        val layoutKey: String,
    ) : Component()
}
