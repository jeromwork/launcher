package com.launcher.fake.apps

import com.launcher.api.apps.OpenAppResult
import com.launcher.api.result.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 009 contract test for [FakeOpenAppDispatcher] (Phase 4).
 */
class FakeOpenAppDispatcherContractTest {

    @Test
    fun records_launch_history() = runTest {
        val d = FakeOpenAppDispatcher()
        d.openApp("com.a")
        d.openApp("com.b")
        assertEquals(listOf("com.a", "com.b"), d.launchHistory)
    }

    @Test
    fun scripted_response_per_package_wins() = runTest {
        val d = FakeOpenAppDispatcher(
            scriptedResponses = mapOf(
                "com.absent" to OpenAppResult.OpenedPlayStore,
                "com.gone" to OpenAppResult.OpenedWebPlayStore,
            ),
        )
        val r1 = d.openApp("com.absent")
        val r2 = d.openApp("com.gone")
        val r3 = d.openApp("com.real")
        assertEquals(OpenAppResult.OpenedPlayStore, (r1 as Outcome.Success).value)
        assertEquals(OpenAppResult.OpenedWebPlayStore, (r2 as Outcome.Success).value)
        assertEquals(OpenAppResult.Launched, (r3 as Outcome.Success).value)
    }
}
