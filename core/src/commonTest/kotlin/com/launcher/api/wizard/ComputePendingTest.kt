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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * State-of-device pre-flight per data-model.md §2.1 + plan.md Phase 1.
 * Validates FR-013 and SC-003 (Сценарий 4 — settings/state diagnostic):
 *  A) nothing applied → pending = all steps.
 *  B) ROLE_HOME Applied → pending excludes that step.
 *  C) all SystemSetting steps Applied + UIChoice answered + hint dismissed
 *     → pending = []. (Config-check master)
 *  D) Indeterminate (e.g. OEM quirk on accessibility detection) → step is
 *     KEPT in pending (graceful — never silently treat ambiguous as Applied).
 */
class ComputePendingTest {

    private val header = ConfigDocumentHeader(
        schemaVersion = 1,
        id = "wizard-manifest.task7-compute-pending",
        name = "n",
        description = "d",
        deviceClass = listOf("android-phone"),
    )

    private fun manifest(steps: List<StepEntry>): WizardManifest = WizardManifest(
        header = header,
        body = WizardManifestBody(
            appFamilyId = "task7-fixture",
            autoOrder = false,
            steps = steps,
        ),
    )

    private fun buildEngine(
        systemSettingPort: SystemSettingPort = FakeSystemSettingAdapter(),
        userPreferencesStore: UserPreferencesStore = InMemoryUserPreferencesStore(),
        dismissedHintsStore: DismissedHintsStore = InMemoryDismissedHintsStore(),
    ): WizardEngineImpl = WizardEngineImpl(
        steps = emptyMap(),
        checkpointStore = InMemoryCheckpointStore(),
        userPreferencesStore = userPreferencesStore,
        configSource = FakeConfigSource(),
        clock = FakeClock(),
        diagnostics = RecordingDiagnosticEmitter(),
        systemSettingPort = systemSettingPort,
        dismissedHintsStore = dismissedHintsStore,
    )

    private val roleHomeEntry = StepEntry(
        stepType = WireStepType.SystemSetting,
        refId = "android.role.home",
        canSkip = false,
        criticality = WireCriticality.Required,
    )
    private val postNotifEntry = StepEntry(
        stepType = WireStepType.SystemSetting,
        refId = "android.permission.POST_NOTIFICATIONS",
        canSkip = false,
        criticality = WireCriticality.Required,
    )
    private val themeEntry = StepEntry(
        stepType = WireStepType.UIChoice,
        refId = "theme",
        canSkip = false,
        criticality = WireCriticality.Required,
    )
    private val hintEntry = StepEntry(
        stepType = WireStepType.TutorialHint,
        refId = "first-tile-hint",
        canSkip = true,
        criticality = WireCriticality.Optional,
    )

    private val allSteps = listOf(roleHomeEntry, postNotifEntry, themeEntry, hintEntry)

    @Test
    fun scenarioA_nothingApplied_pendingIsAll() = runTest {
        val engine = buildEngine()
        val pending = engine.computePending(manifest(allSteps))
        assertEquals(allSteps, pending)
    }

    @Test
    fun scenarioB_roleHomeApplied_pendingExcludesIt() = runTest {
        val port = FakeSystemSettingAdapter(
            initialStatuses = mapOf("android.role.home" to SettingStatus.Applied),
        )
        val engine = buildEngine(systemSettingPort = port)
        val pending = engine.computePending(manifest(allSteps))
        assertEquals(
            listOf(postNotifEntry, themeEntry, hintEntry),
            pending,
        )
    }

    @Test
    fun scenarioC_allApplied_pendingIsEmpty() = runTest {
        val port = FakeSystemSettingAdapter(
            initialStatuses = mapOf(
                "android.role.home" to SettingStatus.Applied,
                "android.permission.POST_NOTIFICATIONS" to SettingStatus.Applied,
            ),
        )
        val prefs = InMemoryUserPreferencesStore(
            initial = UserPreferences(theme = ThemeChoice.Light),
        )
        val hints = InMemoryDismissedHintsStore()
        hints.markDismissed("first-tile-hint")
        val engine = buildEngine(
            systemSettingPort = port,
            userPreferencesStore = prefs,
            dismissedHintsStore = hints,
        )
        val pending = engine.computePending(manifest(allSteps))
        assertTrue(pending.isEmpty(), "expected empty pending; got $pending")
    }

    @Test
    fun scenarioD_indeterminate_stepIncluded() = runTest {
        // Indeterminate must NOT be treated as Applied — keep the step in pending
        // so the wizard surfaces it (self-attest path, OEM accessibility quirks).
        val port = FakeSystemSettingAdapter(
            initialStatuses = mapOf("android.role.home" to SettingStatus.Indeterminate),
        )
        val engine = buildEngine(systemSettingPort = port)
        val pending = engine.computePending(manifest(listOf(roleHomeEntry)))
        assertEquals(listOf(roleHomeEntry), pending)
    }
}
