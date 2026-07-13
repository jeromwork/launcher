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

    /**
     * T010 — LauncherRole component (FR-002).
     * No parameters. Provider claims / releases the Android HOME role.
     */
    @Serializable
    @SerialName("LauncherRole")
    object LauncherRole : Component()

    /**
     * T011 — Theme component (FR-003).
     * Flat fields only per D3; `ThemeRef` sugar expands at write time (androidMain).
     */
    @Serializable
    @SerialName("Theme")
    data class Theme(
        val paletteSeedHex: String,
        val typographyScale: TypographyScale,
        val shapeStyle: ShapeStyle,
        val darkMode: Boolean,
    ) : Component()

    /**
     * T012 — Language component (FR-004).
     * `locale` sentinel `"system"` means follow OS locale.
     * Null locale is a validation error (see [ValidationError.NullLocale]).
     */
    @Serializable
    @SerialName("Language")
    data class Language(
        val locale: String,
    ) : Component()

    /**
     * T013 — StatusBarPolicy component (FR-005).
     * No parameters. Provider hides the system status bar (kiosk mode).
     */
    @Serializable
    @SerialName("StatusBarPolicy")
    object StatusBarPolicy : Component()
}
