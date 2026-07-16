package com.launcher.preset.model

import kotlinx.serialization.Serializable

@Serializable
enum class WizardBehavior { Interactive, AutoApply, InitialDefault }

/**
 * Lifecycle state of one [Entity] inside a [Profile].
 *
 * [Unverifiable] (T127-005, FR-014) — the setting was applied on the user's word
 * and the OS exposes no read-back (e.g. hiding the system status bar is a chain
 * of intents with no query API). Recording [Applied] there would be a lie:
 * `BootCheck` would trust a fiction, and re-checking on every cold start would
 * nag the user forever. Only the interactive paths (Wizard / RunMode.Single) may
 * record it; `BootCheck` skips such entities entirely.
 */
@Serializable
enum class ComponentStatus { Pending, Applied, Failed, Skipped, Unverifiable }

/**
 * Semantic + structural marker on a [Component] (T127-004, FR-001).
 *
 * Multiple tags per component are expected — `Sos` is `Presentation` AND `Tile`
 * AND `Safety` AND `Emergency` at once; queries select on any combination.
 *
 * Mental model (ADR-012): this is a **label-selector** system (closest industrial
 * analogue: Kubernetes `matchLabels`), not canonical ECS archetype filtering.
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
