package com.launcher.test.fakes

import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus

/**
 * Programmable [GmsAvailabilityPort] for spec 010 unit and Compose tests
 * (CLAUDE.md rule 6 — mock-first; serves T050 `GmsHardBlockTest` +
 * `FirstLaunchActivity` integration tests).
 *
 * Mutate [status] between invocations to simulate cold-start state transitions.
 */
class FakeGmsAvailabilityPort(
    var status: GmsStatus = GmsStatus.Available,
) : GmsAvailabilityPort {
    override suspend fun status(): GmsStatus = status
}
