package com.launcher.api.edit

import com.launcher.api.result.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [EditUiProfileSelector] — verifies FR-008 mapping + FR-008b
 * fallback split (built-in unknown vs custom preset).
 *
 * Covers SC-005: profile selection 100% correct for всех built-in presets +
 * explicit refuse for custom (FR-008b).
 *
 * Trace: spec 014 T020.
 */
class EditUiProfileSelectorTest {

    @Test
    fun workspace_preset_maps_to_admin_profile() {
        val result = EditUiProfileSelector.selectProfile("workspace")
        assertEquals(Outcome.Success(EditUiProfile.AdminProfile), result)
    }

    @Test
    fun simple_launcher_preset_maps_to_senior_profile() {
        val result = EditUiProfileSelector.selectProfile("simple-launcher")
        assertEquals(Outcome.Success(EditUiProfile.SeniorProfile), result)
    }

    @Test
    fun other_built_in_preset_falls_back_to_admin_profile() {
        // `launcher` is a built-in FlowPreset but not explicitly mapped — per
        // FR-008b, falls back silently to AdminProfile (least restrictive).
        val result = EditUiProfileSelector.selectProfile("launcher")
        assertEquals(Outcome.Success(EditUiProfile.AdminProfile), result)
    }

    @Test
    fun custom_preset_refuses_explicitly_per_FR_008b() {
        val result = EditUiProfileSelector.selectProfile("custom-via-configurator")
        assertEquals(
            Outcome.Failure(EditError.ProfileSelectionRequiresCapabilityRegistry),
            result,
        )
    }

    @Test
    fun empty_preset_refuses_explicitly() {
        // Empty string is not a built-in FlowPreset slug → custom-preset path.
        val result = EditUiProfileSelector.selectProfile("")
        assertEquals(
            Outcome.Failure(EditError.ProfileSelectionRequiresCapabilityRegistry),
            result,
        )
    }

    @Test
    fun case_sensitive_built_in_match() {
        // Slugs are case-sensitive per FlowPreset enum (wire-format identifier).
        val result = EditUiProfileSelector.selectProfile("WORKSPACE")
        assertTrue(
            result is Outcome.Failure,
            "uppercase 'WORKSPACE' is NOT a registered slug — must refuse",
        )
    }
}
