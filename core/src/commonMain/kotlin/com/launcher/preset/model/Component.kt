package com.launcher.preset.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Component — sealed hierarchy of what-is-configurable in the launcher.
 *
 * Each subtype declares its own parameter set. Instances are attached to an
 * [Entity] inside `Profile.components`.
 *
 * The model has **three orthogonal axes** — do not conflate them
 * (see `docs/architecture/preset-model.md`):
 *
 *  1. **Lifecycle** — `Preset.wizardFlow` / `settingsMap` / `activeComponents`:
 *     *when* a component appears (first-run wizard, Settings screen, applied now).
 *  2. **Semantic** — [tags]: *what* a component is about (Presentation, Safety,
 *     Accessibility, …). Multiple tags per subtype are expected: [Sos] carries
 *     `[Presentation, Tile, Safety, Emergency]`.
 *  3. **Structural** — `Entity.parentId` + the [Workspace] / [Flow] /
 *     [ToolbarButton] subtypes: *where on the screen* it lives. Storage stays
 *     flat; the tree is computed by queries (`preset/query/ProfileQuery.kt`).
 *
 * Per TASK-127 (FR-002) every subtype MUST carry a non-empty [tags] default —
 * enforced at build time by `ComponentTagsFitnessTest`.
 *
 * Adding new subtypes: additive only per rule 5 (wire-format versioning);
 * removing subtypes requires a migration writer per rule 5 + a
 * `decision-supersedes` task per rule 11.
 */
@Serializable
sealed class Component {

    /** Semantic + structural markers. See [Tag]. Never empty (FR-002). */
    abstract val tags: Set<Tag>

    @Serializable
    @SerialName("AppTile")
    data class AppTile(
        val packageName: String,
        val labelKey: String,
        val iconKey: String? = null,
        val pinProtected: Boolean = false,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Tile),
    ) : Component()

    @Serializable
    @SerialName("FontSize")
    data class FontSize(
        val scale: Float,
        override val tags: Set<Tag> = setOf(Tag.Appearance, Tag.Accessibility),
    ) : Component()

    @Serializable
    @SerialName("Sos")
    data class Sos(
        val shareLocation: Boolean = true,
        val autoAnswer: Boolean = true,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Tile, Tag.Safety, Tag.Emergency),
    ) : Component()

    /**
     * Bottom toolbar **container**. Its buttons are separate [ToolbarButton]
     * entities whose `parentId` points at this one (T127-008, FR-013).
     */
    @Serializable
    @SerialName("Toolbar")
    data class Toolbar(
        /** Legacy flat list; superseded by [ToolbarButton] children. */
        val items: List<String> = emptyList(),
        val layoutKey: String,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Toolbar),
    ) : Component()

    /**
     * T010 — LauncherRole component (FR-002). Provider claims / releases the
     * Android HOME role.
     *
     * T127-007: was an `object`; converted to a data class because an object
     * cannot carry an overridable constructor default for [tags]. Wire-compatible —
     * `{"type":"LauncherRole"}` with no fields still deserializes.
     */
    @Serializable
    @SerialName("LauncherRole")
    data class LauncherRole(
        override val tags: Set<Tag> = setOf(Tag.System),
    ) : Component()

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
        override val tags: Set<Tag> = setOf(Tag.Appearance),
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
        override val tags: Set<Tag> = setOf(Tag.System),
    ) : Component()

    /**
     * T013 — StatusBarPolicy component (FR-005). Provider hides the system status
     * bar (kiosk mode).
     *
     * T127-007: object → data class (same reason as [LauncherRole]).
     *
     * Android exposes **no read-back** for this setting, so the provider returns
     * [Outcome.NeedsUserConfirmation] and the entity ends up
     * [ComponentStatus.Unverifiable] rather than a dishonest `Applied` (FR-014).
     */
    @Serializable
    @SerialName("StatusBarPolicy")
    data class StatusBarPolicy(
        override val tags: Set<Tag> = setOf(Tag.System),
    ) : Component()

    // ---- structural subtypes (T127-008, FR-013, spec Q7) ----

    /**
     * Screen root. Children (by `Entity.parentId`): [Flow] entities + one
     * [Toolbar] entity.
     */
    @Serializable
    @SerialName("Workspace")
    data class Workspace(
        val layoutKey: String = "single",
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Workspace),
    ) : Component()

    /**
     * One tab / page inside a [Workspace]. Owns its own grid — `layoutKey` lives
     * here rather than on `Profile`, so each tab can differ (FR-013).
     * [order] drives left-to-right placement.
     */
    @Serializable
    @SerialName("Flow")
    data class Flow(
        val titleKey: String,
        val layoutKey: String = "2x3",
        val order: Int = 0,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Flow),
    ) : Component()

    /**
     * One toolbar button. `parentId` points at the [Toolbar]; [targetFlowId]
     * names the [Flow] entity this button switches to (validated —
     * [ValidationError.DanglingTargetRef]).
     */
    @Serializable
    @SerialName("ToolbarButton")
    data class ToolbarButton(
        val targetFlowId: String,
        val labelKey: String,
        val iconKey: String? = null,
        val order: Int = 0,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.ToolbarButton),
    ) : Component()
}
