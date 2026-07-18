package com.launcher.preset.query

import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Tag
import com.launcher.preset.model.WizardBehavior

/**
 * Shared fixtures for the query tests (T127-014 / T127-015, reshaped TASK-136).
 *
 * [hierarchicalProfile] models the owner's target screen (spec US-4):
 * a workspace with three flows and a bottom toolbar whose three buttons each
 * switch to one flow. Storage is **flat** — the tree lives in `parentId`.
 *
 * TASK-136: entities are free bags — the [component] plus a [LifecycleState]
 * marker in `components`, and the [tags] on the entity (was on the component).
 * [defaultTagsFor] reproduces TASK-127's per-component tag defaults so the tag
 * selectors keep the same semantics under the canonical model.
 */
internal fun entity(
    id: String,
    component: Component,
    parentId: String? = null,
    state: LifecycleState = LifecycleState.Applied,
    critical: Boolean = false,
    wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    tags: Set<Tag> = defaultTagsFor(component),
): Entity = Entity(
    id = id,
    components = listOf(component, state),
    tags = tags,
    wizardBehavior = wizardBehavior,
    critical = critical,
    parentId = parentId,
)

internal fun profileOf(vararg entities: Entity): Profile = Profile(
    basedOnPreset = "test-preset",
    presetVersion = 2,
    layoutKey = "grid",
    entities = entities.toList(),
)

internal fun appTile(
    id: String,
    pkg: String,
    parentId: String?,
    state: LifecycleState = LifecycleState.Applied,
) = entity(
    id = id,
    component = Component.AppTile(packageName = pkg, labelKey = "label.$id"),
    parentId = parentId,
    state = state,
)

/** The TASK-127 per-component tag defaults, now stamped on the entity (CL-4). */
internal fun defaultTagsFor(component: Component): Set<Tag> = when (component) {
    is Component.AppTile -> setOf(Tag.Presentation, Tag.Tile)
    is Component.FontSize -> setOf(Tag.Appearance, Tag.Accessibility)
    is Component.Sos -> setOf(Tag.Presentation, Tag.Tile, Tag.Safety, Tag.Emergency)
    is Component.Toolbar -> setOf(Tag.Presentation, Tag.Toolbar)
    is Component.LauncherRole -> setOf(Tag.System)
    is Component.Theme -> setOf(Tag.Appearance)
    is Component.Language -> setOf(Tag.System)
    is Component.StatusBarPolicy -> setOf(Tag.System)
    is Component.Workspace -> setOf(Tag.Presentation, Tag.Workspace)
    is Component.Flow -> setOf(Tag.Presentation, Tag.Flow)
    is Component.ToolbarButton -> setOf(Tag.Presentation, Tag.ToolbarButton)
    is LifecycleState -> emptySet()
}

/**
 * workspace ws-main
 *  ├── flow-calls  (order 0) → tile-whatsapp, sos-primary
 *  ├── flow-apps   (order 1) → tile-settings
 *  ├── flow-info   (order 2) → (no tiles)
 *  └── toolbar-main → btn-calls (0), btn-apps (1), btn-info (2)
 * plus one root-level StatusBarPolicy with LifecycleState.Unverifiable.
 */
internal fun hierarchicalProfile(): Profile = profileOf(
    entity("ws-main", Component.Workspace()),
    entity("flow-apps", Component.Flow(titleKey = "flow.apps", order = 1), parentId = "ws-main"),
    entity("flow-calls", Component.Flow(titleKey = "flow.calls", order = 0), parentId = "ws-main"),
    entity("flow-info", Component.Flow(titleKey = "flow.info", order = 2), parentId = "ws-main"),
    appTile("tile-whatsapp", "com.whatsapp", parentId = "flow-calls"),
    entity("sos-primary", Component.Sos(), parentId = "flow-calls", critical = true),
    appTile("tile-settings", "com.android.settings", parentId = "flow-apps"),
    entity("toolbar-main", Component.Toolbar(layoutKey = "bottom-bar"), parentId = "ws-main"),
    entity(
        "btn-apps",
        Component.ToolbarButton(targetFlowId = "flow-apps", labelKey = "btn.apps", order = 1),
        parentId = "toolbar-main",
    ),
    entity(
        "btn-calls",
        Component.ToolbarButton(targetFlowId = "flow-calls", labelKey = "btn.calls", order = 0),
        parentId = "toolbar-main",
    ),
    entity(
        "btn-info",
        Component.ToolbarButton(targetFlowId = "flow-info", labelKey = "btn.info", order = 2),
        parentId = "toolbar-main",
    ),
    entity("statusbar", Component.StatusBarPolicy, state = LifecycleState.Unverifiable),
)

/** Simple launcher (US-1): tiles only, no Workspace/Flow/Toolbar — degenerate tree. */
internal fun flatProfile(): Profile = profileOf(
    appTile("tile-settings", "com.android.settings", parentId = null),
    appTile("tile-phone", "com.android.dialer", parentId = null),
    entity("font", Component.FontSize(scale = 1.6f)),
)
