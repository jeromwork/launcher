package com.launcher.preset.engine

import com.launcher.preset.fakes.FakeCapabilityContract
import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.model.Preset
import com.launcher.preset.model.ValidationError
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.model.WizardFlowEntry
import com.launcher.preset.roundtrip.mvpPool
import com.launcher.preset.roundtrip.simpleLauncherPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PresetValidatorTest {

    // We use existing MVP subclasses as capability stand-ins:
    //   Sos ~ SignIn (provides CloudSession)
    //   Toolbar ~ CloudConsumer (requires CloudSession)
    private val cloudContract = FakeCapabilityContract(
        requires = mapOf(Component.Toolbar::class to setOf(CapabilityFlag.CloudSession)),
        provides = mapOf(Component.Sos::class to setOf(CapabilityFlag.CloudSession)),
    )
    private val emptyContract = FakeCapabilityContract()

    @Test
    fun validPreset_returnsEmpty() {
        val errors = PresetValidator(emptyContract).validate(simpleLauncherPreset(), mvpPool())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun schemaVersionTooHigh_returnsError() {
        val preset = simpleLauncherPreset().copy(schemaVersion = 9)
        val errors = PresetValidator(emptyContract).validate(preset, mvpPool())
        assertEquals(1, errors.size)
        assertIs<ValidationError.SchemaVersionUnsupported>(errors.single())
    }

    @Test
    fun unknownPoolRef_returnsError() {
        val preset = simpleLauncherPreset().copy(
            activeComponents = simpleLauncherPreset().activeComponents +
                com.launcher.preset.model.ActiveComponentEntry("does-not-exist"),
        )
        val errors = PresetValidator(emptyContract).validate(preset, mvpPool())
        assertTrue(errors.any { it is ValidationError.UnknownPoolRef })
    }

    @Test
    fun validOrdering_providerBeforeConsumer_returnsEmpty() {
        val preset = presetWith(
            listOf(
                wf("font-tile", 1, WizardBehavior.Interactive),
                wf("sos-main", 2, WizardBehavior.Interactive),
                wf("toolbar-minimal", 3, WizardBehavior.InitialDefault),
            )
        )
        assertTrue(PresetValidator(cloudContract).validate(preset, mvpPool()).isEmpty())
    }

    @Test
    fun malformedOrdering_consumerBeforeProvider_returnsCapabilityMissing() {
        val preset = presetWith(
            listOf(
                wf("toolbar-minimal", 1, WizardBehavior.InitialDefault),
                wf("sos-main", 2, WizardBehavior.Interactive),
            )
        )
        val errors = PresetValidator(cloudContract).validate(preset, mvpPool())
        val missing = errors.filterIsInstance<ValidationError.CapabilityMissing>()
        assertEquals(1, missing.size)
        assertEquals(setOf(CapabilityFlag.CloudSession), missing.single().missing)
    }

    @Test
    fun optionalPath_componentWithoutRequires_notBlocked() {
        val preset = presetWith(listOf(wf("font-tile", 1, WizardBehavior.Interactive)))
        assertTrue(PresetValidator(cloudContract).validate(preset, mvpPool()).isEmpty())
    }

    private fun presetWith(flow: List<WizardFlowEntry>): Preset =
        Preset(
            presetId = "test",
            version = 1,
            layoutKey = "layout.grid.2x3",
            wizardFlow = flow,
            activeComponents = flow.map {
                com.launcher.preset.model.ActiveComponentEntry(it.poolRef)
            },
        )

    private fun wf(id: String, order: Int, behavior: WizardBehavior) = WizardFlowEntry(
        poolRef = id,
        order = order,
        wizardTitleKey = "wizard.$id.title",
        behavior = behavior,
    )
}
