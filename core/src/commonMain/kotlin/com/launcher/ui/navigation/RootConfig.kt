package com.launcher.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Top-level navigation configurations rendered by [RootComponent].
 *
 * Marked @Serializable so Decompose can persist the stack across configuration
 * changes / process death (per Article III §3 — app-critical behaviour reliable
 * across recreation).
 *
 * New top-level screens go here; pushing a new screen = `nav.push(NewScreen(...))`.
 */
@Serializable
sealed interface RootConfig {

    @Serializable
    data object FirstLaunch : RootConfig

    @Serializable
    data object Home : RootConfig

    @Serializable
    data class FlowDetail(val flowId: String) : RootConfig

    @Serializable
    data object Settings : RootConfig

    @Serializable
    data object AddFlowWizard : RootConfig

    @Serializable
    data class AddSlotWizard(val flowId: String) : RootConfig

    @Serializable
    data object AdminDevices : RootConfig

    // ─── Spec 009 admin-mode-flows screens ─────────────────────────────

    @Serializable
    data class Editor(val linkId: String) : RootConfig

    @Serializable
    data class History(val linkId: String, val rollbackAllowed: Boolean = true) : RootConfig

    @Serializable
    data class ContactsManage(val linkId: String) : RootConfig

    @Serializable
    data class OpenAppPicker(val linkId: String) : RootConfig

    @Serializable
    data class PhoneHealth(val linkId: String) : RootConfig
}
