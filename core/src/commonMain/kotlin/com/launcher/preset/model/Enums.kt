package com.launcher.preset.model

import kotlinx.serialization.Serializable

@Serializable
enum class WizardBehavior { Interactive, AutoApply, InitialDefault }

/**
 * Semantic + structural marker on an [Entity] (TASK-136, canonical ECS).
 *
 * A tag is a **zero-data marker** (ECS FAQ: "a tag is a component that has no
 * data") carried by the [Entity], not by the [Component] — one entity carries
 * several tags at once (`Sos` is `Presentation` AND `Tile` AND `Safety` AND
 * `Emergency`); queries select on any combination. Assigned explicitly at spawn
 * (bundle) or by composing code — never auto-derived from components (CL-4).
 *
 * `Set<Tag>` is the compact encoding of Fleks `Snapshot.tags` (a separate marker
 * list). Exit ramp (Decision): a tag that must carry data / need a typed query is
 * promoted from an enum value to a marker/data [Component] — additive, same bag.
 *
 * Additive-only per rule 5, with an honest caveat: an *older reader* fails loud on
 * an unknown enum name (kotlinx.serialization has no per-element leniency for enum
 * collections). Safe while a Profile is same-device/same-binary; a lenient reader
 * is mandatory before cross-device exchange ships — see TASK-131.
 */
@Serializable
enum class Tag {
    // ---- semantic domain: what the component is about ----
    /** Visible to the owner on some UI surface. */
    Presentation,
    /** Visual override (font scale, theme, ...). */
    Appearance,
    /** OS-level setting (rotation lock, kiosk, launcher role). */
    System,
    /** Safety-related (SOS, emergency call routing). */
    Safety,
    /** Permissions, features, integrations. */
    Capabilities,
    /** Call, SMS, messenger, contact list. */
    Communication,
    /** WCAG / TalkBack / senior-safe override. */
    Accessibility,
    /** Triggered in an emergency (SOS, panic). */
    Emergency,

    // ---- structural role: which part of the screen it is (T127-004, Q7) ----
    /** Renders in a flow's tile grid. */
    Tile,
    /** The bottom toolbar container. */
    Toolbar,
    /** Screen root; parent of flows and the toolbar. */
    Workspace,
    /** One tab/page inside a workspace. */
    Flow,
    /** One button inside a toolbar; switches to its target flow. */
    ToolbarButton,
}

enum class RunMode { Wizard, BootCheck, Single, RemotePush }

@Serializable
enum class Vendor { Xiaomi, Samsung, Huawei, GoogleTV, GenericAndroid, iOS }

@Serializable
enum class Sensitivity { Normal, High, Admin }

@Serializable
enum class TypographyScale { Small, Medium, Large, ExtraLarge }

@Serializable
enum class ShapeStyle { Rounded, Sharp, Mixed }
