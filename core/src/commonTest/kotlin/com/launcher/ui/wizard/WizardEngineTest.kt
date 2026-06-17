package com.launcher.ui.wizard

import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSummary
import com.launcher.api.wizard.Criticality
import com.launcher.api.wizard.DiagnosticEvent
import com.launcher.api.wizard.PendingStep
import com.launcher.api.wizard.StepParams
import com.launcher.api.wizard.StepResult
import com.launcher.api.wizard.StepType
import com.launcher.api.wizard.WizardCheckpoint
import com.launcher.api.wizard.WizardOutcome
import com.launcher.api.wizard.WizardStep
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.ConfigDocumentHeader
import com.launcher.api.wizard.data.StepEntry
import com.launcher.api.wizard.data.SystemSettingEntry
import com.launcher.api.wizard.data.SystemSettingsPoolBody
import com.launcher.api.wizard.data.UICustomizationPoolBody
import com.launcher.api.wizard.data.UIOptionEntry
import com.launcher.api.wizard.data.WireCriticality
import com.launcher.api.wizard.data.WireDetectionStrategy
import com.launcher.api.wizard.data.WireSettingMechanism
import com.launcher.api.wizard.data.WireStepType
import com.launcher.api.wizard.data.WireUIOptionKind
import com.launcher.api.wizard.data.WizardManifest
import com.launcher.api.wizard.data.WizardManifestBody
import com.launcher.api.wizard.fakes.FakeClock
import com.launcher.api.wizard.fakes.FakeConfigSource
import com.launcher.api.wizard.fakes.InMemoryCheckpointStore
import com.launcher.api.wizard.fakes.InMemoryUserPreferencesStore
import com.launcher.api.wizard.fakes.RecordingDiagnosticEmitter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WizardEngineTest {

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

    private fun fixtureManifest(steps: List<StepEntry>): WizardManifest = WizardManifest(
        header = ConfigDocumentHeader(
            schemaVersion = 1,
            id = "wizard-manifest.test",
            name = "n",
            description = "d",
            deviceClass = listOf("android-phone"),
        ),
        body = WizardManifestBody(
            appFamilyId = "test-family",
            autoOrder = false,
            steps = steps,
        ),
    )

    @Test
    fun traversal_completedOnAllSteps() = runTest {
        val checkpoints = InMemoryCheckpointStore()
        val prefs = InMemoryUserPreferencesStore()
        val source = FakeConfigSource()
        val emitter = RecordingDiagnosticEmitter()
        val uiStep = CapturingStep(StepType.UIChoice, "ru")
        val sysStep = CapturingStep(StepType.SystemSetting, "Applied")
        val hintStep = CapturingStep(StepType.TutorialHint, "dismissed")
        val engine = WizardEngineImpl(
            steps = mapOf(
                StepType.UIChoice to uiStep,
                StepType.SystemSetting to sysStep,
                StepType.TutorialHint to hintStep,
            ),
            checkpointStore = checkpoints,
            userPreferencesStore = prefs,
            configSource = source,
            clock = FakeClock(),
            diagnostics = emitter,
        )

        val manifest = fixtureManifest(
            steps = listOf(
                StepEntry(WireStepType.UIChoice, "language", canSkip = false, criticality = WireCriticality.Required),
                StepEntry(WireStepType.UIChoice, "tileSet", canSkip = false, criticality = WireCriticality.Required),
                StepEntry(WireStepType.SystemSetting, "android.role.home", canSkip = true, criticality = WireCriticality.Required),
                StepEntry(WireStepType.TutorialHint, "first-tile-hint", canSkip = true, criticality = WireCriticality.Optional),
                StepEntry(WireStepType.UIChoice, "theme", canSkip = true, criticality = WireCriticality.Optional),
            ),
        )

        val outcome = engine.run(manifest)
        assertIs<WizardOutcome.Completed>(outcome)
        assertEquals(3, uiStep.invocations.size)
        assertEquals(1, sysStep.invocations.size)
        assertEquals(1, hintStep.invocations.size)
        assertTrue(prefs.isWizardCompleted("test-family"))
        val started = emitter.snapshot().filterIsInstance<DiagnosticEvent.WizardStarted>()
        val completed = emitter.snapshot().filterIsInstance<DiagnosticEvent.WizardCompleted>()
        assertEquals(1, started.size)
        assertEquals(1, completed.size)
    }

    @Test
    fun resumesFromCheckpoint() = runTest {
        val checkpoints = InMemoryCheckpointStore()
        // Pre-seed checkpoint at step 2 (already past steps 0 and 1).
        checkpoints.save(
            WizardCheckpoint(
                schemaVersion = 1,
                manifestId = "wizard-manifest.test",
                currentStepIndex = 2,
                answers = mapOf(
                    "language" to JsonPrimitive("en"),
                    "tileSet" to JsonPrimitive("classic-6"),
                ),
            ),
        )
        val prefs = InMemoryUserPreferencesStore()
        val source = FakeConfigSource()
        val emitter = RecordingDiagnosticEmitter()
        val uiStep = CapturingStep(StepType.UIChoice, "ack")
        val sysStep = CapturingStep(StepType.SystemSetting, "Applied")
        val engine = WizardEngineImpl(
            steps = mapOf(
                StepType.UIChoice to uiStep,
                StepType.SystemSetting to sysStep,
            ),
            checkpointStore = checkpoints,
            userPreferencesStore = prefs,
            configSource = source,
            clock = FakeClock(),
            diagnostics = emitter,
        )
        val manifest = fixtureManifest(
            steps = listOf(
                StepEntry(WireStepType.UIChoice, "language", canSkip = false, criticality = WireCriticality.Required),
                StepEntry(WireStepType.UIChoice, "tileSet", canSkip = false, criticality = WireCriticality.Required),
                StepEntry(WireStepType.SystemSetting, "android.role.home", canSkip = true, criticality = WireCriticality.Required),
            ),
        )
        engine.run(manifest)
        // Only step 2 (system setting) should have been invoked — language/tileSet
        // already captured in checkpoint.
        assertEquals(0, uiStep.invocations.size)
        assertEquals(1, sysStep.invocations.size)
    }

    @Test
    fun corruptCheckpoint_restartsFromZero() = runTest {
        val checkpoints = InMemoryCheckpointStore()
        // schemaVersion=999 means "future version" — engine treats as invalid.
        checkpoints.save(
            WizardCheckpoint(
                schemaVersion = 999,
                manifestId = "wizard-manifest.test",
                currentStepIndex = 1,
                answers = emptyMap(),
            ),
        )
        val prefs = InMemoryUserPreferencesStore()
        val source = FakeConfigSource()
        val uiStep = CapturingStep(StepType.UIChoice, "ack")
        val engine = WizardEngineImpl(
            steps = mapOf(StepType.UIChoice to uiStep),
            checkpointStore = checkpoints,
            userPreferencesStore = prefs,
            configSource = source,
            clock = FakeClock(),
            diagnostics = RecordingDiagnosticEmitter(),
        )
        engine.run(fixtureManifest(steps = listOf(StepEntry(WireStepType.UIChoice, "language"))))
        assertEquals(1, uiStep.invocations.size)
        assertEquals(0, uiStep.invocations.first().stepIndex)
    }

    @Test
    fun diffPending_returnsOnlyNewSteps() = runTest {
        val source = FakeConfigSource()
        val engine = WizardEngineImpl(
            steps = emptyMap(),
            checkpointStore = InMemoryCheckpointStore(),
            userPreferencesStore = InMemoryUserPreferencesStore(),
            configSource = source,
            clock = FakeClock(),
            diagnostics = RecordingDiagnosticEmitter(),
        )
        val saved = fixtureManifest(
            steps = listOf(
                StepEntry(WireStepType.UIChoice, "language", criticality = WireCriticality.Required),
                StepEntry(WireStepType.UIChoice, "tileSet", criticality = WireCriticality.Required),
            ),
        )
        val current = fixtureManifest(
            steps = listOf(
                StepEntry(WireStepType.UIChoice, "language", criticality = WireCriticality.Required),
                StepEntry(WireStepType.UIChoice, "tileSet", criticality = WireCriticality.Required),
                StepEntry(WireStepType.UIChoice, "fontScale", criticality = WireCriticality.Optional),
                StepEntry(WireStepType.SystemSetting, "android.permission.CALL_PHONE", criticality = WireCriticality.Required),
            ),
        )
        val pending: List<PendingStep> = engine.diffPending(saved, current)
        assertEquals(2, pending.size)
        assertEquals("fontScale", pending[0].stepEntry.refId)
        assertEquals(Criticality.Optional, pending[0].criticality)
        assertEquals("android.permission.CALL_PHONE", pending[1].stepEntry.refId)
        assertEquals(Criticality.Required, pending[1].criticality)
    }

    @Test
    fun autoOrder_requiredBeforeOptional() = runTest {
        val source = FakeConfigSource(
            summaries = mapOf(
                ConfigKind.UICustomizationPool to listOf(
                    ConfigSummary("ui-customization.ui-pool", "n", "d", listOf("*")),
                ),
                ConfigKind.SystemSettingsPool to listOf(
                    ConfigSummary("system-settings.android-pool", "n", "d", listOf("android-phone")),
                ),
            ),
            documents = mapOf(
                (ConfigKind.UICustomizationPool to "ui-customization.ui-pool") to
                    ConfigDocument.UICustomizationPoolDoc(
                        header = ConfigDocumentHeader(1, "ui-customization.ui-pool", "n", "d", listOf("*")),
                        body = UICustomizationPoolBody(
                            platform = "*",
                            options = listOf(
                                UIOptionEntry("language", WireUIOptionKind.SimpleChoice, "qk", null, WireCriticality.Required, "en"),
                                UIOptionEntry("theme", WireUIOptionKind.SimpleChoice, "qk", null, WireCriticality.Optional, "auto"),
                            ),
                        ),
                    ),
                (ConfigKind.SystemSettingsPool to "system-settings.android-pool") to
                    ConfigDocument.SystemSettingsPoolDoc(
                        header = ConfigDocumentHeader(1, "system-settings.android-pool", "n", "d", listOf("android-phone")),
                        body = SystemSettingsPoolBody(
                            platform = "android",
                            settings = listOf(
                                SystemSettingEntry(
                                    id = "android.role.home",
                                    mechanism = WireSettingMechanism.DeepLink,
                                    criticality = WireCriticality.Required,
                                    canSkip = true,
                                    deepLink = null,
                                    androidMinApi = null,
                                    dependsOn = emptyList(),
                                    detectionStrategy = WireDetectionStrategy.Programmatic,
                                    labelKey = "l",
                                    descriptionKey = "d",
                                ),
                                SystemSettingEntry(
                                    id = "android.permission.CALL_PHONE",
                                    mechanism = WireSettingMechanism.StandardPermission,
                                    criticality = WireCriticality.Optional,
                                    canSkip = true,
                                    deepLink = null,
                                    androidMinApi = null,
                                    dependsOn = emptyList(),
                                    detectionStrategy = WireDetectionStrategy.Programmatic,
                                    labelKey = "l",
                                    descriptionKey = "d",
                                ),
                            ),
                        ),
                    ),
            ),
        )
        val uiStep = CapturingStep(StepType.UIChoice, "ack")
        val sysStep = CapturingStep(StepType.SystemSetting, "Applied")
        val engine = WizardEngineImpl(
            steps = mapOf(
                StepType.UIChoice to uiStep,
                StepType.SystemSetting to sysStep,
            ),
            checkpointStore = InMemoryCheckpointStore(),
            userPreferencesStore = InMemoryUserPreferencesStore(),
            configSource = source,
            clock = FakeClock(),
            diagnostics = RecordingDiagnosticEmitter(),
        )
        val autoManifest = WizardManifest(
            header = ConfigDocumentHeader(1, "auto", "n", "d", listOf("android-phone")),
            body = WizardManifestBody(appFamilyId = "auto", autoOrder = true, steps = null),
        )
        engine.run(autoManifest)
        // Two Required first, then two Optional.
        val invokedOrder: List<String> =
            uiStep.invocations.map { it.refId } + sysStep.invocations.map { it.refId }
        // Required first: language + android.role.home; optional: theme + CALL_PHONE.
        // Sorting by stepIndex assigned through the engine.
        val byStepIndex = (uiStep.invocations + sysStep.invocations)
            .sortedBy { it.stepIndex }
            .map { it.refId }
        assertEquals(
            listOf("language", "android.role.home", "theme", "android.permission.CALL_PHONE"),
            byStepIndex,
            "expected required steps first, then optional; got $invokedOrder",
        )
    }
}
