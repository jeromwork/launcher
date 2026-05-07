package com.launcher.core.events

import com.launcher.api.PackageChangeReason
import com.launcher.api.ProjectEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventRouterTest {

    @Test
    fun seriesOfPackageSetChangedCoalescesToLastAfterDebounceWindow() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val router = EventRouter(scope, packageSetDebounceMs = 200L)
        val received = mutableListOf<ProjectEvent>()
        val collectJob = scope.launch {
            router.events.collect { received.add(it) }
        }

        scheduler.runCurrent()

        router.emit(ProjectEvent.PackageSetChanged(PackageChangeReason.PACKAGE_ADDED))
        router.emit(ProjectEvent.PackageSetChanged(PackageChangeReason.PACKAGE_REMOVED))
        router.emit(ProjectEvent.PackageSetChanged(PackageChangeReason.PACKAGE_REPLACED))
        scheduler.runCurrent()

        assertEquals(
            "Burst within debounce window must not emit yet",
            0,
            received.size,
        )

        scheduler.advanceTimeBy(200L)
        scheduler.runCurrent()

        assertEquals(1, received.size)
        val only = received.single() as ProjectEvent.PackageSetChanged
        assertEquals(PackageChangeReason.PACKAGE_REPLACED, only.reason)

        collectJob.cancel()
        scope.cancel()
    }

    @Test
    fun profileChangedIsNotHeldBehindPackageSetDebounce() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val router = EventRouter(scope, packageSetDebounceMs = 200L)
        val received = mutableListOf<ProjectEvent>()
        val collectJob = scope.launch {
            router.events.collect { received.add(it) }
        }

        scheduler.runCurrent()

        router.emit(ProjectEvent.PackageSetChanged(PackageChangeReason.PACKAGE_ADDED))
        scheduler.runCurrent()
        router.emit(ProjectEvent.ProfileChanged(profileGeneration = 42))
        scheduler.runCurrent()

        assertEquals(1, received.size)
        assertTrue(received.single() is ProjectEvent.ProfileChanged)

        scheduler.advanceTimeBy(200L)
        scheduler.runCurrent()

        assertEquals(2, received.size)
        assertTrue(received[1] is ProjectEvent.PackageSetChanged)

        collectJob.cancel()
        scope.cancel()
    }
}
