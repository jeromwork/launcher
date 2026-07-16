package com.launcher.preset.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Component — sealed hierarchy of what-is-configurable in the launcher.
 *
 * Each subtype declares its own parameter set. Instances of Component subtypes
 * are attached to `Entity` entries inside `Profile.components`.
 *
 * Per TASK-127, each subtype MUST carry a `tags: Set<Tag>` field expressing
 * the **SEMANTIC dimension** — what the component is about (Presentation,
 * Safety, Accessibility, …). Multiple tags are allowed on a single subtype
 * (e.g. `Sos` carries `[Presentation, Safety, Emergency]`).
 *
 * This is orthogonal to the **LIFECYCLE dimension** expressed by
 * `Preset.wizardFlow / settingsMap / activeComponents` — those describe *when*
 * a component is used, not *what* it is about.
 *
 * See `docs/architecture/preset-model.md` § "Two orthogonal dimensions" for the
 * canonical explanation and § "Adding a New Component" for the checklist
 * when introducing a new subtype.
 *
 * Adding new subtypes: additive only per rule 5 (wire-format versioning);
 * removing subtypes requires migration writer per rule 5 + `decision-supersedes`
 * task per rule 11.
 */
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
