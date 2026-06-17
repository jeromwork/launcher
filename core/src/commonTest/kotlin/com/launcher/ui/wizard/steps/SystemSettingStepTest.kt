package com.launcher.ui.wizard.steps

import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.StepParams
import com.launcher.api.wizard.StepResult
import com.launcher.api.wizard.fakes.FakeClock
import com.launcher.api.wizard.fakes.FakeSystemSettingAdapter
import com.launcher.api.wizard.fakes.InMemoryUserPreferencesStore
import com.launcher.api.wizard.fakes.RecordingDiagnosticEmitter
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Per the post-smoke fix on 2026-06-17, SystemSettingStep must return
 * StepResult.AnswerCaptured on every adapter outcome path — never Skipped —
 * so the engine never fails a Required step because the user declined a
 * permission. The denial is recorded via DiagnosticEvent + sentinel
 * answer payload, and surfaces later as a Settings badge in S-1+.
 *
 * Each test:
 *   1. Sets the FakeSystemSettingAdapter status to NotApplied (otherwise
 *      step short-circuits with "Applied" before even awaiting UI).
 *   2. Sets the applyOrPrompt outcome to the path under test.
 *   3. Launches `step.execute()` in a coroutine that awaits the host.
 *   4. Resolves the StepHost with AnswerCaptured (simulating the user
 *      tapping the Next button on the wizard screen).
 *   5. Asserts the returned StepResult.
 */
class SystemSettingStepTest {

    private fun makeStep(
        host: StepHost,
        applyOutcomes: Map<String, ApplyResult>,
        statuses: Map<String, SettingStatus> = mapOf("setting.x" to SettingStatus.NotApplied),
    ): Pair<SystemSettingStep, RecordingDiagnosticEmitter> {
        val emitter = RecordingDiagnosticEmitter()
        val step = SystemSettingStep(
            host = host,
            systemSettingPort = FakeSystemSettingAdapter(
                initialStatuses = statuses,
                applyOutcomes = applyOutcomes,
            ),
            userPreferencesStore = InMemoryUserPreferencesStore(),
            diagnostics = emitter,
            clock = FakeClock(),
        )
        return step to emitter
    }

    @Test
    fun denied_returnsAnswerCaptured_notSkipped() = runTest {
        val host = StepHost()
        val (step, emitter) = makeStep(
            host = host,
            applyOutcomes = mapOf("setting.x" to ApplyResult.Denied),
        )
        val async = async { step.execute(StepParams("setting.x", emptyMap(), 0, 1)) }
        while (host.pending == null) { /* spin until host receives params */ }
        host.resolve(StepResult.AnswerCaptured(JsonPrimitive("user-tap")))
        val result = async.await()

        val captured = assertIs<StepResult.AnswerCaptured>(result)
        assertEquals(JsonPrimitive("Denied"), captured.answer)
        assertTrue(
            emitter.snapshot().any {
                it is com.launcher.api.wizard.DiagnosticEvent.WizardStepDenied &&
                    it.settingId == "setting.x" && !it.isPermanent
            },
        )
    }

    @Test
    fun permanentlyDenied_returnsAnswerCaptured_withPermanentFlag() = runTest {
        val host = StepHost()
        val (step, emitter) = makeStep(
            host = host,
            applyOutcomes = mapOf("setting.x" to ApplyResult.PermanentlyDenied),
        )
        val async = async { step.execute(StepParams("setting.x", emptyMap(), 0, 1)) }
        while (host.pending == null) { /* spin */ }
        host.resolve(StepResult.AnswerCaptured(JsonPrimitive("user-tap")))
        val result = async.await()

        val captured = assertIs<StepResult.AnswerCaptured>(result)
        assertEquals(JsonPrimitive("PermanentlyDenied"), captured.answer)
        assertTrue(
            emitter.snapshot().any {
                it is com.launcher.api.wizard.DiagnosticEvent.WizardStepDenied &&
                    it.settingId == "setting.x" && it.isPermanent
            },
        )
    }

    @Test
    fun unsupportedMechanism_returnsAnswerCaptured() = runTest {
        val host = StepHost()
        val (step, _) = makeStep(
            host = host,
            applyOutcomes = mapOf("setting.x" to ApplyResult.UnsupportedMechanism),
        )
        val async = async { step.execute(StepParams("setting.x", emptyMap(), 0, 1)) }
        while (host.pending == null) { /* spin */ }
        host.resolve(StepResult.AnswerCaptured(JsonPrimitive("user-tap")))
        val captured = assertIs<StepResult.AnswerCaptured>(async.await())
        assertEquals(JsonPrimitive("UnsupportedMechanism"), captured.answer)
    }

    @Test
    fun failed_emitsFallbackWarning_andReturnsAnswerCaptured() = runTest {
        val host = StepHost()
        val (step, emitter) = makeStep(
            host = host,
            applyOutcomes = mapOf("setting.x" to ApplyResult.Failed("io")),
        )
        val async = async { step.execute(StepParams("setting.x", emptyMap(), 0, 1)) }
        while (host.pending == null) { /* spin */ }
        host.resolve(StepResult.AnswerCaptured(JsonPrimitive("user-tap")))
        val captured = assertIs<StepResult.AnswerCaptured>(async.await())
        assertEquals(JsonPrimitive("Failed: io"), captured.answer)
        assertTrue(
            emitter.snapshot().any {
                it is com.launcher.api.wizard.DiagnosticEvent.FallbackWarning &&
                    it.area == "system-setting"
            },
        )
    }

    @Test
    fun preAppliedStatus_shortCircuitsToAppliedAnswer() = runTest {
        val host = StepHost()
        val (step, _) = makeStep(
            host = host,
            applyOutcomes = emptyMap(),
            statuses = mapOf("setting.x" to SettingStatus.Applied),
        )
        val result = step.execute(StepParams("setting.x", emptyMap(), 0, 1))
        val captured = assertIs<StepResult.AnswerCaptured>(result)
        assertEquals(JsonPrimitive("Applied"), captured.answer)
    }

    @Test
    fun notSupportedOnPlatform_returnsSkipped() = runTest {
        // NotSupportedOnPlatform DOES return Skipped (the engine treats Skipped
        // as "advance" but won't apply the Required-step check because the
        // entry presumably won't list this id on this platform). This is the
        // single Skipped exit path SystemSettingStep keeps.
        val host = StepHost()
        val (step, _) = makeStep(
            host = host,
            applyOutcomes = emptyMap(),
            statuses = mapOf("setting.x" to SettingStatus.NotSupportedOnPlatform),
        )
        val result = step.execute(StepParams("setting.x", emptyMap(), 0, 1))
        assertEquals(StepResult.Skipped, result)
    }
}
