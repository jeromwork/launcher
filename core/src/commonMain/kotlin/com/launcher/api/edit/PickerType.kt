package com.launcher.api.edit

/**
 * Tile types в unified picker (FR-018).
 *
 * Visibility filtering:
 *  - `Workspace` target → all 5 tabs visible (FR-018).
 *  - `Simple Launcher` target → only [Application], [Contact], [Document]
 *    (FR-019: Widget+Action hidden — privacy + simplicity на бабушкином home).
 *
 * Functional implementation status в F-014.0:
 *  - [Application]: fully implemented (existing спека 005 OpenApp slot).
 *  - [Contact]: fully implemented (existing спека 011 Contact slot).
 *  - [Document]: fully implemented (existing спека 012 Document slot).
 *  - [Widget]: **placeholder в F-014** — tap opens "В разработке" screen
 *    (FR-018, FR-018a). Real rendering deferred → TODO-UX-027.
 *  - [Action]: **placeholder в F-014** — tap opens "В разработке" screen
 *    (FR-018, FR-018a). Real impl (SOS / phone / flashlight) deferred → TODO-UX-028.
 */
enum class PickerType {
    Application,
    Contact,
    Document,
    Widget,
    Action,
}
