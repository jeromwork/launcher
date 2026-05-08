package com.launcher.core.actions

import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderId
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.action.ProviderState
import com.launcher.core.actions.handlers.ActionHandler
import com.launcher.core.actions.handlers.HandlerContext
import com.launcher.core.events.EventRouter
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.TimeSource

/**
 * Spec 005 §9 / NFR / T641: dispatch latency p95 must stay ≤ 50 ms in
 * a synthetic single-thread loop. The test invokes the dispatcher 100
 * times against an `Available`-pinned registry and a no-op handler;
 * sorts the elapsed-times sample and asserts the 95th percentile.
 *
 * The benchmark is informative, not a hard CI gate: noise on shared
 * runners can spike a single iteration. We give a generous ceiling
 * (50 ms p95) per the spec; if a regression slows the dispatcher
 * tenfold, this test will still catch it.
 */
class AndroidActionDispatcherLatencyTest {

    private fun fakeRegistry(): ProviderRegistry = object : ProviderRegistry {
        override fun availability(providerId: ProviderId) = ProviderAvailability.Available
        override fun snapshot() = emptyList<ProviderState>()
        override val updates = emptyFlow<List<ProviderState>>()
    }

    @Test
    fun dispatch_p95LatencyUnder50ms() = runTest {
        val handler = object : ActionHandler {
            override suspend fun handle(action: Action, ctx: HandlerContext) = DispatchResult.Ok
        }
        val dispatcher = AndroidActionDispatcher(
            handlers = mapOf(ProviderId.PHONE to handler),
            providerRegistry = fakeRegistry(),
            eventRouter = mockk<EventRouter>(relaxed = true),
            handlerContext = mockk(relaxed = true),
            timeSource = { 0L },
        )
        val action = Action(providerId = ProviderId.PHONE, payload = ActionPayload.Phone("+1"))

        // Warm up — first JVM run pays JIT cost, not what we measure.
        repeat(20) { dispatcher.dispatch(action) }

        val timer = TimeSource.Monotonic
        val samples = LongArray(100)
        for (i in 0 until 100) {
            val mark = timer.markNow()
            dispatcher.dispatch(action)
            samples[i] = mark.elapsedNow().inWholeMicroseconds
        }
        samples.sort()
        val p95Micros = samples[(samples.size * 0.95).toInt()]
        val p95Ms = p95Micros / 1000.0
        assertTrue(
            "p95 dispatch latency exceeded 50 ms: $p95Ms ms (samples sorted µs: ${samples.toList()})",
            p95Ms < 50.0,
        )
    }
}
