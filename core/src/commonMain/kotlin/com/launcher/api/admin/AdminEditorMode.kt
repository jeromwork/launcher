package com.launcher.api.admin

/**
 * Editor screen mode (spec 009 FR-001, FR-005). View ⇒ all edit actions
 * disabled (read-only browse of `/config/current`); Edit ⇒ local
 * mutations enabled, autosave active per spec 008 §FR-056.
 */
enum class AdminEditorMode {
    View,
    Edit,
}
