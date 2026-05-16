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

    /**
     * App picker (FR-034). [returnTo] is the route to push after selection:
     *   - null → pop only (browse-only flow);
     *   - TileEditReturn → re-push TileEdit с pendingOpenAppPackage so the
     *     edit form pre-fills (spec 009 G3 round-trip).
     */
    @Serializable
    data class OpenAppPicker(
        val linkId: String,
        val returnTo: OpenAppPickerReturn? = null,
    ) : RootConfig

    @Serializable
    sealed interface OpenAppPickerReturn {
        @Serializable
        data class TileEditReturn(
            val linkId: String,
            val flowId: String,
            val slotId: String,
        ) : OpenAppPickerReturn
    }

    @Serializable
    data class PhoneHealth(val linkId: String) : RootConfig

    /**
     * Tile-edit form (spec 009 FR-011). flowId + slotId identify the slot
     * being edited. [pendingOpenAppPackage] propagates from
     * OpenAppPicker's return so RootComponent can re-construct the
     * TileEditComponent с pre-filled package after rotation / nav round
     * trip.
     */
    @Serializable
    data class TileEdit(
        val linkId: String,
        val flowId: String,
        val slotId: String,
        val pendingOpenAppPackage: String? = null,
    ) : RootConfig
}
