package com.launcher.app.wizard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.adapters.wizard.BundledConfigSource
import com.launcher.api.localization.StringResolver
import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.Clock
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.DiagnosticEmitter
import com.launcher.api.wizard.DiagnosticEvent
import com.launcher.api.wizard.DismissedHintsState
import com.launcher.api.wizard.DismissedHintsStore
import com.launcher.api.wizard.PermissionRequestPort
import com.launcher.api.wizard.PermissionResult
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.StepResult
import com.launcher.api.wizard.StepType
import com.launcher.api.wizard.SystemSettingPort
import com.launcher.api.wizard.UserPreferences
import com.launcher.api.wizard.UserPreferencesStore
import com.launcher.api.wizard.WizardCheckpoint
import com.launcher.api.wizard.WizardCheckpointStore
import com.launcher.api.wizard.WizardEngine
import com.launcher.api.wizard.WizardOutcome
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.WizardManifest
import com.launcher.ui.wizard.TutorialHintManager
import com.launcher.ui.wizard.WizardEngineImpl
import com.launcher.ui.wizard.steps.StepHost
import com.launcher.ui.wizard.steps.SystemSettingStep
import com.launcher.ui.wizard.steps.TutorialHintStep
import com.launcher.ui.wizard.steps.UIChoiceStep
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Spec 015 T119 — wizard end-to-end integration test (Robolectric).
 *
 * Drives WizardEngineImpl through the real BundledConfigSource — same
 * code path the Koin graph wires in production. In-memory ports stand in
 * for DataStore + Android system services so the test runs in JVM only.
 *
 * Asserts:
 *   - Engine processes 12 entries (6 UI + 6 system settings) from
 *     bundled assets/wizard/ ui-customization + system-settings JSON.
 *   - WizardOutcome.Completed returned.
 *   - userPreferencesStore.isWizardCompleted("simple-launcher") == true.
 *   - WizardCompleted diagnostic event fired.
 *
 * Run:
 *   ./gradlew :app:testMockBackendDebugUnitTest --tests "*WizardEngineIntegrationTest"
 *
 * Note: ports are inlined here (commonTest fakes are not on :app's test
 * classpath; cross-module test code sharing would require a separate
 * test-fixtures artefact).
 */
@RunWith(RobolectricTestRunner::class)
class WizardEngineIntegrationTest {

    @Test
    fun wholeWizard_completes_and_marksPresetDone() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()

        val configSource: ConfigSource = BundledConfigSource(app)
        val checkpoints = InMemoryCheckpointStore()
        val prefs = InMemoryUserPreferencesStore()
        val emitter = RecordingDiagnosticEmitter()
        val clock = FixedClock()
        val resolver: StringResolver = NoopStringResolver()
        val dismissedHintsStore = InMemoryDismissedHints()
        val hintManager = TutorialHintManager(dismissedHintsStore, resolver, clock)

        val permissionPort = AlwaysGrantedPermissionPort()
        // System setting port: all settings already Applied → no UI tap needed.
        val systemSettingPort = AlwaysAppliedSystemSettingPort()

        val uiHost = StepHost()
        val sysHost = StepHost()
        val hintHost = StepHost()

        val engine: WizardEngine = WizardEngineImpl(
            steps = mapOf(
                StepType.UIChoice to UIChoiceStep(uiHost),
                StepType.SystemSetting to SystemSettingStep(
                    host = sysHost,
                    systemSettingPort = systemSettingPort,
                    userPreferencesStore = prefs,
                    diagnostics = emitter,
                    clock = clock,
                ),
                StepType.TutorialHint to TutorialHintStep(hintHost, hintManager),
            ),
            checkpointStore = checkpoints,
            userPreferencesStore = prefs,
            configSource = configSource,
            clock = clock,
            diagnostics = emitter,
            systemSettingPort = systemSettingPort,
            dismissedHintsStore = dismissedHintsStore,
        )

        val manifest = loadManifest(configSource)
        assertEquals(true, manifest.body.autoOrder)

        // UI tapper — auto-resolves every UIChoice step with "ack".
        val uiTapper = GlobalScope.async {
            while (true) {
                val pending = uiHost.pending
                if (pending != null) {
                    uiHost.resolve(StepResult.AnswerCaptured(JsonPrimitive("ack")))
                }
                delay(10)
            }
        }

        try {
            val outcome = engine.run(manifest)
            assertTrue("expected Completed but got $outcome", outcome is WizardOutcome.Completed)
            val completed = outcome as WizardOutcome.Completed
            assertTrue(prefs.isWizardCompleted("wizard-manifest.simple-launcher"))
            assertEquals(12, completed.initialConfig.answers.size)
            assertTrue(
                "expected WizardCompleted event",
                emitter.snapshot().any { it is DiagnosticEvent.WizardCompleted },
            )
        } finally {
            uiTapper.cancel()
        }
    }

    private suspend fun loadManifest(configSource: ConfigSource): WizardManifest {
        val summaries = configSource.list(ConfigKind.WizardManifest)
        val id = summaries.firstOrNull()?.id ?: error("no bundled wizard manifest")
        val result = configSource.load(ConfigKind.WizardManifest, id)
        val success = result as? ConfigSourceResult.Success ?: error("load failed: $result")
        val doc = success.document as ConfigDocument.Manifest
        return WizardManifest(doc.header, doc.body)
    }

    // ─── inlined ports ──────────────────────────────────────────────────────

    private class InMemoryCheckpointStore : WizardCheckpointStore {
        private val map = mutableMapOf<String, WizardCheckpoint>()
        override suspend fun load(manifestId: String) = map[manifestId]
        override suspend fun save(checkpoint: WizardCheckpoint) {
            map[checkpoint.manifestId] = checkpoint
        }
        override suspend fun clear(manifestId: String) { map.remove(manifestId) }
    }

    private class InMemoryUserPreferencesStore : UserPreferencesStore {
        private val flow = MutableStateFlow(UserPreferences())
        override suspend fun save(prefs: UserPreferences) { flow.value = prefs }
        override fun observe(): Flow<UserPreferences> = flow.asStateFlow()
        override suspend fun current(): UserPreferences = flow.value
        override suspend fun markWizardCompleted(presetId: String) {
            flow.value = flow.value.copy(
                wizardCompletedPresets = flow.value.wizardCompletedPresets + presetId,
            )
        }
        override suspend fun isWizardCompleted(presetId: String) =
            presetId in flow.value.wizardCompletedPresets
    }

    private class InMemoryDismissedHints : DismissedHintsStore {
        private val set = mutableSetOf<String>()
        override suspend fun isDismissed(hintId: String) = hintId in set
        override suspend fun markDismissed(hintId: String) { set += hintId }
        override suspend fun clear(hintId: String) { set -= hintId }
        override suspend fun current() = DismissedHintsState(set.toSet())
    }

    private class FixedClock : Clock {
        override fun nowEpochMillis(): Long = 1_700_000_000_000L
    }

    private class NoopStringResolver : StringResolver {
        override fun resolve(key: String, args: Map<String, Any>) = key
        override fun resolvePlural(key: String, count: Int, args: Map<String, Any>) = key
        override fun currentLocaleTag() = "en"
    }

    private class RecordingDiagnosticEmitter : DiagnosticEmitter {
        private val events = mutableListOf<DiagnosticEvent>()
        override fun emit(event: DiagnosticEvent) { events += event }
        fun snapshot() = events.toList()
    }

    private class AlwaysGrantedPermissionPort : PermissionRequestPort {
        override suspend fun request(permission: String) = PermissionResult.Granted
        override fun isGranted(permission: String) = true
        override fun isPermanentlyDenied(permission: String) = false
    }

    private class AlwaysAppliedSystemSettingPort : SystemSettingPort {
        override suspend fun status(settingId: String): SettingStatus = SettingStatus.Applied
        override suspend fun applyOrPrompt(settingId: String): ApplyResult = ApplyResult.Applied
    }
}
