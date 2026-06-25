package com.launcher.api.wizard

import com.launcher.api.wizard.data.ConfigDocumentHeader
import com.launcher.api.wizard.data.StepEntry
import com.launcher.api.wizard.data.WireCriticality
import com.launcher.api.wizard.data.WireStepType
import com.launcher.api.wizard.data.WizardManifest
import com.launcher.api.wizard.data.WizardManifestBody
import com.launcher.api.wizard.fakes.FakeClock
import com.launcher.api.wizard.fakes.FakeConfigSource
import com.launcher.api.wizard.fakes.FakeSystemSettingAdapter
import com.launcher.api.wizard.fakes.InMemoryCheckpointStore
import com.launcher.api.wizard.fakes.InMemoryDismissedHintsStore
import com.launcher.api.wizard.fakes.InMemoryUserPreferencesStore
import com.launcher.api.wizard.fakes.RecordingDiagnosticEmitter
import com.launcher.ui.wizard.WizardEngineImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TASK-7 Phase 6 — runWalkThrough variant (FR-014a / Сценарий 5).
 * Verifies the engine traverses every step even when computePending
 * would short-circuit (everything Applied).
 */
class RunWalkThroughTest {

    private class CapturingStep(
        override val stepType: StepType,
        private val answer: String,
    ) : WizardStep {
        override val canSkip: Boolean = true
        override val canGoBack: Boolean = true
        val invocations = mutableListOf<StepParams>()
        override suspend fun execute(params: StepParams): StepResult {
            invocations += params
            return StepResult.AnswerCaptured(JsonPrimitive(answer))
        }
    }

    private fun manifest(steps: List<StepEntry>): WizardManifest = WizardManifest(
        header = ConfigDocumentHeader(
            schemaVersion = 1,
            id = "wizard-manifest.walkthrough",
            name = "n",
            description = "d",
            deviceClass = listOf("android-phone"),
        ),
        body = WizardManifestBody(
            appFamilyId = "task7-walkthrough-fixture",
            autoOrder = false,
            steps = steps,
        ),
    )

    private class ModeCapturingStep : WizardStep {
        override val stepType: StepType = StepType.SystemSetting
        override val canSkip: Boolean = true
        override val canGoBack: Boolean = true
        var engineRef: WizardEngine? = null
        val capturedModes = mutableListOf<WizardEngine.Mode>()
        override suspend fun execute(params: StepParams): StepResult {
            val running = engineRef?.currentState()?.value as? WizardState.Running
            running?.let { capturedModes += it.mode }
            return StepResult.Skipped
        }
    }

    @Test
    fun runWalkThrough_setsWalkThroughModeOnState() = runTest {
        val port = FakeSystemSettingAdapter()
        val prefs = InMemoryUserPreferencesStore()
        val recordingStep = ModeCapturingStep()
        val engine = WizardEngineImpl(
            steps = mapOf(StepType.SystemSetting to recordingStep),
            checkpointStore = InMemoryCheckpointStore(),
            userPreferencesStore = prefs,
            configSource = FakeConfigSource(),
            clock = FakeClock(),
            diagnostics = RecordingDiagnosticEmitter(),
            systemSettingPort = port,
            dismissedHintsStore = InMemoryDismissedHintsStore(),
        )
        recordingStep.engineRef = engine

        val m = manifest(
            steps = listOf(
                StepEntry(
                    WireStepType.SystemSetting,
                    "android.role.home",
                    canSkip = true,
                    criticality = WireCriticality.Optional,
                ),
            ),
        )

        engine.runWalkThrough(m)
        assertEquals(listOf(WizardEngine.Mode.WalkThrough), recordingStep.capturedModes)

        recordingStep.capturedModes.clear()
        engine.run(m)
        // FakeSystemSettingAdapter defaults to NotApplied, so computePending
        // is non-empty and run() traverses the step in Wizard mode.
        assertTrue(
            recordingStep.capturedModes.all { it == WizardEngine.Mode.Wizard },
            "expected Wizard mode in state during run(); got ${recordingStep.capturedModes}",
        )
    }

    @Test
    fun runWalkThrough_traversesAllSteps_evenWhenComputePendingWouldBeEmpty() = runTest {
        // All system settings reported Applied; UIChoice has a persisted value.
        val port = FakeSystemSettingAdapter(
            initialStatuses = mapOf("android.role.home" to SettingStatus.Applied),
        )
        val prefs = InMemoryUserPreferencesStore(
            initial = UserPreferences(theme = ThemeChoice.Light),
        )
        val sysStep = CapturingStep(StepType.SystemSetting, "ack")
        val uiStep = CapturingStep(StepType.UIChoice, "ack")
        val engine = WizardEngineImpl(
            steps = mapOf(
                StepType.SystemSetting to sysStep,
                StepType.UIChoice to uiStep,
            ),
            checkpointStore = InMemoryCheckpointStore(),
            userPreferencesStore = prefs,
            configSource = FakeConfigSource(),
            clock = FakeClock(),
            diagnostics = RecordingDiagnosticEmitter(),
            systemSettingPort = port,
            dismissedHintsStore = InMemoryDismissedHintsStore(),
        )

        val m = manifest(
            steps = listOf(
                StepEntry(WireStepType.SystemSetting, "android.role.home", canSkip = false, criticality = WireCriticality.Required),
                StepEntry(WireStepType.UIChoice, "theme", canSkip = false, criticality = WireCriticality.Required),
            ),
        )

        // run() would short-circuit because pending is empty → Completed without invoking steps.
        val runOutcome = engine.run(m)
        assertIs<WizardOutcome.Completed>(runOutcome)
        assertEquals(0, sysStep.invocations.size, "run() short-circuits when everything applied")
        assertEquals(0, uiStep.invocations.size, "run() short-circuits when everything applied")

        // runWalkThrough() must traverse all steps regardless.
        val outcome = engine.runWalkThrough(m)
        assertIs<WizardOutcome.Completed>(outcome)
        assertEquals(1, sysStep.invocations.size, "walk-through visits SystemSetting step")
        assertEquals(1, uiStep.invocations.size, "walk-through visits UIChoice step")
    }
}
