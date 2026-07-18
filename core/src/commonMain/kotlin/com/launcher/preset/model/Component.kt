package com.launcher.preset.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Component — the closed set of what-is-configurable in the launcher (canonical
 * ECS, TASK-136). Each subtype is a plain data holder; an [Entity] carries a
 * **free bag** of them (`Entity.components`), composed after assembly.
 *
 * `sealed interface` keeps the type-set **closed** — the exhaustive `when` for
 * serialization + coverage tests survives, so compile-time safety is preserved
 * while gaining composition (FR-002). Adding a subtype later is additive
 * (`@SerialName`), no edit to existing subtypes.
 *
 * **Three orthogonal axes** — do not conflate them
 * (see `docs/architecture/preset-model.md`):
 *
 *  1. **Lifecycle** — [LifecycleState] component in the bag + `Preset.wizardFlow`
 *     / `settingsMap` / `activeComponents`: *when* / *in what apply-state*.
 *  2. **Semantic** — [Entity.tags]: *what* a component is about (Presentation,
 *     Safety, Accessibility, …). Tags live on the entity, not the component
 *     (TASK-136 — canonical ECS: a tag is a zero-data marker on the entity).
 *  3. **Structural** — `Entity.parentId` + the [Workspace] / [Flow] /
 *     [ToolbarButton] subtypes: *where on the screen* it lives. Storage stays
 *     flat; the tree is computed by queries (`preset/query/ProfileQuery.kt`).
 *
 * The closed set = **11 domain-data subtypes** (below) **+ [LifecycleState]** (a
 * state component, its own file). At most one component per Kotlin type per
 * entity (CL-3, fitness-enforced) ⇒ `entity.get<T>()` is unambiguous.
 */
@Serializable
sealed interface Component {

    @Serializable
    @SerialName("AppTile")
    data class AppTile(
        val packageName: String,
        val labelKey: String,
        val iconKey: String? = null,
        val pinProtected: Boolean = false,
    ) : Component

    @Serializable
    @SerialName("FontSize")
    data class FontSize(
        val scale: Float,
    ) : Component

    @Serializable
    @SerialName("Sos")
    data class Sos(
        val shareLocation: Boolean = true,
        val autoAnswer: Boolean = true,
    ) : Component

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
    ) : Component

    /**
     * LauncherRole component (FR-002). Provider claims / releases the Android
     * HOME role. No fields — `{"type":"LauncherRole"}` deserializes.
     */
    @Serializable
    @SerialName("LauncherRole")
    data object LauncherRole : Component

    /**
     * Theme component (FR-003).
     * Flat fields only per D3; `ThemeRef` sugar expands at write time (androidMain).
     */
    @Serializable
    @SerialName("Theme")
    data class Theme(
        val paletteSeedHex: String,
        val typographyScale: TypographyScale,
        val shapeStyle: ShapeStyle,
        val darkMode: Boolean,
    ) : Component

    /**
     * Language component (FR-004).
     * `locale` sentinel `"system"` means follow OS locale.
     * Null locale is a validation error (see [ValidationError.NullLocale]).
     */
    @Serializable
    @SerialName("Language")
    data class Language(
        val locale: String,
    ) : Component

    /**
     * StatusBarPolicy component (FR-005). Provider hides the system status bar
     * (kiosk mode).
     *
     * Android exposes **no read-back** for this setting, so the provider returns
     * [Outcome.NeedsUserConfirmation] and the entity ends up carrying
     * [LifecycleState.Unverifiable] rather than a dishonest `Applied` (FR-014).
     */
    @Serializable
    @SerialName("StatusBarPolicy")
    data object StatusBarPolicy : Component

    // ---- structural subtypes (T127-008, FR-013, spec Q7) ----

    /**
     * Screen root. Children (by `Entity.parentId`): [Flow] entities + one
     * [Toolbar] entity.
     */
    @Serializable
    @SerialName("Workspace")
    data class Workspace(
        val layoutKey: String = "single",
    ) : Component

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
    ) : Component

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
    ) : Component
}
