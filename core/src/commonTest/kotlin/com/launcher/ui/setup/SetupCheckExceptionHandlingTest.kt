package com.launcher.ui.setup

import com.launcher.api.ProjectEvent
import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.Surface
import com.launcher.test.fakes.FakeSetupCheck
import com.launcher.test.fakes.FakeThrowingSetupCheck
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 010 T078 — verifies SetupCheckEngine wraps every check in try-catch
 * so a single throwing adapter (Xiaomi MIUI `SecurityException`) does not
 * crash Settings; FR-020b diagnostic event emitted exactly once per throw.
 */
class SetupCheckExceptionHandlingTest {

    @Test
    fun throwing_check_becomes_NotConfigured_with_reason_and_emits_event() = runTest {
        val healthy = FakeSetupCheck(
            id = "role_home",
            criticality = Criticality.Required,
            initialStatus = CheckStatus.Ok,
        )
        val throwing = FakeThrowingSetupCheck(
            id = "battery_optimization",
            exception = SecurityException("xiaomi-restricted-apps"),
            criticality = Criticality.Recommended,
        )
        val events = mutableListOf<ProjectEvent.SetupCheckException>()
        val engine = SetupCheckEngine(
            setupChecks = listOf(healthy, throwing),
            emitDiagnostic = { events += it },
            nowMillis = { 1000L },
        )
        engine.refresh()

        val results = engine.results.value
        assertEquals(CheckStatus.Ok, results["role_home"])
        val batteryStatus = results["battery_optimization"]
        assertTrue(batteryStatus is CheckStatus.NotConfigured)
        assertEquals("xiaomi-restricted-apps", (batteryStatus as CheckStatus.NotConfigured).reason)

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals("battery_optimization", event.checkId)
        assertEquals("xiaomi-restricted-apps", event.reason)
    }

    @Test
    fun null_message_throwable_falls_back_to_simple_name() = runTest {
        val throwing = FakeThrowingSetupCheck(
            id = "battery_optimization",
            exception = RuntimeException(),
        )
        val events = mutableListOf<ProjectEvent.SetupCheckException>()
        val engine = SetupCheckEngine(
            setupChecks = listOf(throwing),
            emitDiagnostic = { events += it },
            nowMillis = { 0L },
        )
        engine.refresh()
        val status = engine.results.value["battery_optimization"]
        assertTrue(status is CheckStatus.NotConfigured)
        assertEquals("RuntimeException", (status as CheckStatus.NotConfigured).reason)
    }

    @Test
    fun long_message_truncated_to_200_chars_for_diagnostic() = runTest {
        val longMessage = "x".repeat(500)
        val throwing = FakeThrowingSetupCheck(
            id = "battery_optimization",
            exception = SecurityException(longMessage),
        )
        val events = mutableListOf<ProjectEvent.SetupCheckException>()
        val engine = SetupCheckEngine(
            setupChecks = listOf(throwing),
            emitDiagnostic = { events += it },
            nowMillis = { 0L },
        )
        engine.refresh()
        assertEquals(200, events.single().reason.length)
    }
}
