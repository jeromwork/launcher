package com.launcher.preset.query

import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Entity
import com.launcher.preset.model.Profile
import com.launcher.preset.model.WizardBehavior

/**
 * Shared fixtures for the query tests (T127-014 / T127-015).
 *
 * [hierarchicalProfile] models the owner's target screen (spec US-4):
 * a workspace with three flows and a bottom toolbar whose three buttons each
 * switch to one flow. Note the storage is **flat** — the tree lives in `parentId`.
 */
internal fun entity(
    id: String,
    component: Component,
    parentId: String? = null,
    status: ComponentStatus = ComponentStatus.Applied,
    critical: Boolean = false,
    wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
): Entity = Entity(
    id = id,
    component = component,
    wizardBehavior = wizardBehavior,
    critical = critical,
    status = status,
    parentId = parentId,
)

internal fun profileOf(vararg entities: Entity): Profile = Profile(
    basedOnPreset = "test-preset",
    presetVersion = 2,
    layoutKey = "grid",
    components = entities.toList(),
)

internal fun appTile(id: String, pkg: String, parentId: String?, status: ComponentStatus = ComponentStatus.Applied) =
    entity(
        id = id,
        component = Component.AppTile(packageName = pkg, labelKey = "label.$id"),
        parentId = parentId,
        status = status,
    )

/**
 * workspace ws-main
 *  ├── flow-calls  (order 0) → tile-whatsapp, sos-primary
 *  ├── flow-apps   (order 1) → tile-settings
 *  ├── flow-info   (order 2) → (no tiles)
 *  └── toolbar-main → btn-calls (0), btn-apps (1), btn-info (2)
 * plus one root-level StatusBarPolicy with status Unverifiable.
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
    entity("statusbar", Component.StatusBarPolicy(), status = ComponentStatus.Unverifiable),
)

/** Simple launcher (US-1): tiles only, no Workspace/Flow/Toolbar — degenerate tree. */
internal fun flatProfile(): Profile = profileOf(
    appTile("tile-settings", "com.android.settings", parentId = null),
    appTile("tile-phone", "com.android.dialer", parentId = null),
    entity("font", Component.FontSize(scale = 1.6f)),
)
