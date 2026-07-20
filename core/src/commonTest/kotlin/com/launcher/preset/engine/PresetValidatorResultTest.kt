package com.launcher.preset.engine

import family.wire.WireVersion

import com.launcher.preset.fakes.FakeCapabilityContract
import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.Component
import com.launcher.preset.model.Blueprint
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.ValidationError
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.model.WizardFlowEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T026 (FR-006, FR-019, CL-8).
 *
 * Exercises the typed [PresetValidationResult] surface introduced by T020:
 *   - valid ordering → [PresetValidationResult.Success]
 *   - `requires` violation → [ValidationError.RequiresOrderViolation]
 *   - unknown pool ref → [ValidationError.UnknownComponentId]
 *   - blank `Language.locale` → [ValidationError.NullLocale]
 */
class PresetValidatorResultTest {

    private val emptyContract = FakeCapabilityContract()

    // Pool where `app-tile-whatsapp` requires `launcher-role` earlier in wizardFlow.
    private fun poolWithRequires(): Pool = Pool(
        schemaVersion = WireVersion(2, 0),
        declarations = listOf(
            Blueprint(
                id = "launcher-role",
                components = listOf(Component.LauncherRole),
                wizardBehavior = WizardBehavior.AutoApply,
                critical = true,
                required = true,
            ),
            Blueprint(
                id = "app-tile-whatsapp",
                components = listOf(
                    Component.AppTile(
                        packageName = "com.whatsapp",
                        labelKey = "app_whatsapp_label",
                    ),
                ),
                wizardBehavior = WizardBehavior.AutoApply,
                requires = listOf("launcher-role"),
                required = false,
            ),
        ),
    )

    private fun wf(id: String, order: Int, behavior: WizardBehavior = WizardBehavior.AutoApply) =
        WizardFlowEntry(
            poolRef = id,
            order = order,
            wizardTitleKey = "wizard.$id.title",
            behavior = behavior,
        )

    @Test
    fun validOrdering_returnsSuccess() {
        val pool = poolWithRequires()
        val preset = Preset(
            presetId = "test",
            version = 1,
            layoutKey = "layout.grid.2x3",
            wizardFlow = listOf(
                wf("launcher-role", 1),
                wf("app-tile-whatsapp", 2),
            ),
            activeComponents = listOf(
                ActiveComponentEntry("launcher-role"),
                ActiveComponentEntry("app-tile-whatsapp"),
            ),
        )

        val result = PresetValidator(emptyContract).validateToResult(preset, pool)

        val success = assertIs<PresetValidationResult.Success>(result)
        assertEquals(preset, success.preset)
    }

    @Test
    fun requiresViolation_returnsFailureWithRequiresOrderViolation() {
        val pool = poolWithRequires()
        // Inverted order: app-tile-whatsapp (order=1) BEFORE launcher-role (order=2)
        // → app-tile-whatsapp's requires=[launcher-role] cannot be satisfied.
        val preset = Preset(
            presetId = "test",
            version = 1,
            layoutKey = "layout.grid.2x3",
            wizardFlow = listOf(
                wf("app-tile-whatsapp", 1),
                wf("launcher-role", 2),
            ),
            activeComponents = listOf(
                ActiveComponentEntry("app-tile-whatsapp"),
                ActiveComponentEntry("launcher-role"),
            ),
        )

        val result = PresetValidator(emptyContract).validateToResult(preset, pool)

        val failure = assertIs<PresetValidationResult.Failure>(result)
        val violation = failure.errors.filterIsInstance<ValidationError.RequiresOrderViolation>()
        assertEquals(1, violation.size)
        assertEquals("app-tile-whatsapp", violation.single().offenderId)
        assertEquals("launcher-role", violation.single().missingId)
    }

    @Test
    fun unknownComponentId_returnsFailureWithUnknownComponentId() {
        val pool = poolWithRequires()
        val preset = Preset(
            presetId = "test",
            version = 1,
            layoutKey = "layout.grid.2x3",
            wizardFlow = listOf(
                wf("launcher-role", 1),
                wf("does-not-exist", 2),
            ),
            activeComponents = listOf(
                ActiveComponentEntry("launcher-role"),
                ActiveComponentEntry("does-not-exist"),
            ),
        )

        val result = PresetValidator(emptyContract).validateToResult(preset, pool)

        val failure = assertIs<PresetValidationResult.Failure>(result)
        val unknownIds = failure.errors.filterIsInstance<ValidationError.UnknownComponentId>()
        assertTrue(unknownIds.any { it.id == "does-not-exist" },
            "Expected UnknownComponentId for 'does-not-exist', got: ${failure.errors}")
    }

    @Test
    fun blankLocale_returnsFailureWithNullLocale() {
        val pool = Pool(
            schemaVersion = WireVersion(2, 0),
            declarations = listOf(
                Blueprint(
                    id = "language-blank",
                    components = listOf(Component.Language(locale = "")),
                    wizardBehavior = WizardBehavior.Interactive,
                ),
            ),
        )
        val preset = Preset(
            presetId = "test",
            version = 1,
            layoutKey = "layout.grid.2x3",
            wizardFlow = listOf(wf("language-blank", 1, WizardBehavior.Interactive)),
            activeComponents = listOf(ActiveComponentEntry("language-blank")),
        )

        val result = PresetValidator(emptyContract).validateToResult(preset, pool)

        val failure = assertIs<PresetValidationResult.Failure>(result)
        assertTrue(failure.errors.any { it is ValidationError.NullLocale },
            "Expected NullLocale error, got: ${failure.errors}")
    }
}
