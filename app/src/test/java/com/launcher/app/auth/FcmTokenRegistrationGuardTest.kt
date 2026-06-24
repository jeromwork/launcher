package com.launcher.app.auth

import com.launcher.cloud.api.CloudAvailability
import family.push.api.FcmTokenPublisher
import family.push.api.FcmTokenPublisherError
import family.push.api.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TASK-49 T030 — unit tests for [FcmTokenRegistrationGuard].
 *
 * Robolectric runner because guard calls [android.util.Log]. Pure JVM run
 * would throw "Method not mocked" — same pattern as ConfigUpdatedHandlerTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = android.app.Application::class)
class FcmTokenRegistrationGuardTest {

    private class RecordingPublisher : FcmTokenPublisher {
        val calls: MutableList<String> = mutableListOf()
        var result: Outcome<Unit, FcmTokenPublisherError> = Outcome.Success(Unit)
        override suspend fun publish(fcmToken: String): Outcome<Unit, FcmTokenPublisherError> {
            calls += fcmToken
            return result
        }
    }

    private class FakeCloudAvailability(initial: Boolean = false) : CloudAvailability {
        private val state = MutableStateFlow(initial)
        override val isCloudAvailableFlow: Flow<Boolean> = state.asStateFlow()
        override suspend fun isCloudAvailable(): Boolean = state.value
        fun set(value: Boolean) { state.value = value }
    }

    @Test
    fun cloudAvailableFalseSkipsFirestoreCall() = runTest {
        val publisher = RecordingPublisher()
        val cloud = FakeCloudAvailability(initial = false)
        val guard = FcmTokenRegistrationGuard(
            inner = publisher,
            cloudAvailability = cloud,
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            publishCurrentToken = {},
        )

        val outcome = guard.registerIfAllowed("token-A")

        assertEquals(emptyList<String>(), publisher.calls)
        assert(outcome is Outcome.Success) { "expected Success no-op, got $outcome" }
    }

    @Test
    fun cloudAvailableTrueDelegatesToPublisher() = runTest {
        val publisher = RecordingPublisher()
        val cloud = FakeCloudAvailability(initial = true)
        val guard = FcmTokenRegistrationGuard(
            inner = publisher,
            cloudAvailability = cloud,
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            publishCurrentToken = {},
        )

        val outcome = guard.registerIfAllowed("token-B")

        assertEquals(listOf("token-B"), publisher.calls)
        assert(outcome is Outcome.Success)
    }

    @Test
    fun falseToTrueTransitionTriggersPublishCurrentToken() = runTest {
        val publisher = RecordingPublisher()
        val cloud = FakeCloudAvailability(initial = false)
        var bootstrapInvocations = 0
        FcmTokenRegistrationGuard(
            inner = publisher,
            cloudAvailability = cloud,
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            publishCurrentToken = { bootstrapInvocations += 1 },
        )
        // initial value `false` is the first emission → no transition yet.
        advanceUntilIdle()
        assertEquals(0, bootstrapInvocations)

        cloud.set(true)
        advanceUntilIdle()

        assertEquals(1, bootstrapInvocations)
    }
}
