package com.launcher.core.actions

import com.launcher.api.ProjectEvent
import com.launcher.api.action.Action
import family.wire.WireVersion
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderId
import com.launcher.api.action.ProviderState
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.action.UnavailabilityHint
import com.launcher.core.actions.handlers.ActionHandler
import com.launcher.core.actions.handlers.HandlerContext
import com.launcher.core.events.EventRouter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration test for [AndroidActionDispatcher] covering spec 005 §7.1
 * algorithm and event-emission contract from contracts/diagnostics-events-v2.md.
 *
 * Uses lambda-driven [ActionHandler] fakes (no Robolectric needed — the
 * dispatcher itself touches no Android API; handlers are stubbed).
 */
class AndroidActionDispatcherIntegrationTest {

    private val anyAction = Action(
        providerId = ProviderId.PHONE,
        payload = ActionPayload.Phone("+1"),
    )

    private fun fakeRegistry(
        availabilityByProvider: Map<ProviderId, ProviderAvailability>,
    ): ProviderRegistry = object : ProviderRegistry {
        override fun availability(providerId: ProviderId): ProviderAvailability =
            availabilityByProvider[providerId] ?: ProviderAvailability.Available

        override fun snapshot(): List<ProviderState> = availabilityByProvider.entries
            .map { ProviderState(it.key, it.value) }

        override val updates = emptyFlow<List<ProviderState>>()
    }

    private fun handler(result: DispatchResult): ActionHandler = object : ActionHandler {
        override suspend fun handle(action: Action, ctx: HandlerContext) = result
    }

    private fun dispatcher(
        handlers: Map<ProviderId, ActionHandler>,
        registry: ProviderRegistry = fakeRegistry(emptyMap()),
        eventRouter: EventRouter = mockk(relaxed = true),
        timeSource: () -> Long = { 1_000L },
    ) = AndroidActionDispatcher(
        handlers = handlers,
        providerRegistry = registry,
        eventRouter = eventRouter,
        handlerContext = mockk(relaxed = true),
        timeSource = timeSource,
    )

    // -- spec §7.1 algorithm cases ---------------------------------------

    @Test
    fun success_emitsOk_noFallback() = runTest {
        val router = mockk<EventRouter>(relaxed = true)
        val event = slot<ProjectEvent>()
        every { router.emit(capture(event)) } answers { }

        val r = dispatcher(
            handlers = mapOf(ProviderId.PHONE to handler(DispatchResult.Ok)),
            eventRouter = router,
        ).dispatch(anyAction)

        assertEquals(DispatchResult.Ok, r)
        val emitted = event.captured as ProjectEvent.ActionDispatched
        assertEquals(ProviderId.PHONE, emitted.providerId)
        assertEquals(ProjectEvent.ActionDispatched.ResultKind.Ok, emitted.resultKind)
        assertFalse(emitted.fallbackUsed)
    }

    @Test
    fun missingHandler_unknownProvider_fallsBack() = runTest {
        val unknown = Action(
            providerId = ProviderId.fromWire("smart_assistant"),
            payload = ActionPayload.Custom("ask"),
            fallback = anyAction,
        )
        val router = mockk<EventRouter>(relaxed = true)
        val event = slot<ProjectEvent>()
        every { router.emit(capture(event)) } answers { }

        val r = dispatcher(
            handlers = mapOf(ProviderId.PHONE to handler(DispatchResult.Ok)),
            eventRouter = router,
        ).dispatch(unknown)

        assertEquals(DispatchResult.Ok, r)
        assertTrue((event.captured as ProjectEvent.ActionDispatched).fallbackUsed)
    }

    @Test
    fun missingHandler_noFallback_returnsUnknownInVersion() = runTest {
        val unknown = Action(
            providerId = ProviderId.fromWire("smart_assistant"),
            payload = ActionPayload.Custom("ask"),
        )
        val r = dispatcher(handlers = emptyMap()).dispatch(unknown)
        assertTrue(r is DispatchResult.ProviderUnavailable)
        assertEquals(UnavailabilityHint.UnknownInThisVersion, (r as DispatchResult.ProviderUnavailable).hint)
    }

    @Test
    fun providerMissing_fallsBackToInstalled() = runTest {
        val action = Action(
            providerId = ProviderId.WHATSAPP,
            payload = ActionPayload.WhatsAppMessage("alice"),
            fallback = anyAction,
        )
        val r = dispatcher(
            handlers = mapOf(
                ProviderId.WHATSAPP to handler(DispatchResult.Ok),
                ProviderId.PHONE    to handler(DispatchResult.Ok),
            ),
            registry = fakeRegistry(mapOf(ProviderId.WHATSAPP to ProviderAvailability.Missing(installHint = null))),
        ).dispatch(action)
        assertEquals(DispatchResult.Ok, r)
    }

    @Test
    fun handlerFailure_withFallback_callsFallback() = runTest {
        val action = Action(
            providerId = ProviderId.WHATSAPP,
            payload = ActionPayload.WhatsAppMessage("alice"),
            fallback = anyAction,
        )
        val r = dispatcher(handlers = mapOf(
            ProviderId.WHATSAPP to handler(DispatchResult.Failure("boom")),
            ProviderId.PHONE    to handler(DispatchResult.Ok),
        )).dispatch(action)
        assertEquals(DispatchResult.Ok, r)
    }

    @Test
    fun fallbackChainTooDeep_returnsFailure() = runTest {
        // depth: top + f1 + f2 + f3 → exceeds MAX_FALLBACK_DEPTH (2).
        val deepest  = Action(providerId = ProviderId.WHATSAPP, payload = ActionPayload.WhatsAppMessage("z"))
        val third    = Action(providerId = ProviderId.WHATSAPP, payload = ActionPayload.WhatsAppMessage("y"), fallback = deepest)
        val second   = Action(providerId = ProviderId.WHATSAPP, payload = ActionPayload.WhatsAppMessage("x"), fallback = third)
        val first    = Action(providerId = ProviderId.WHATSAPP, payload = ActionPayload.WhatsAppMessage("w"), fallback = second)

        val r = dispatcher(handlers = mapOf(
            ProviderId.WHATSAPP to handler(DispatchResult.Failure("boom")),
        )).dispatch(first)

        assertTrue(r is DispatchResult.Failure)
        assertTrue((r as DispatchResult.Failure).reason.contains("too deep"))
    }

    @Test
    fun futureSchemaVersionAlone_stillDispatches() = runTest {
        // wire-format.md §3 — schemaVersion is diagnostics only. Before the conversion this
        // action was refused; refusing it was the bug the three-field model fixes.
        val newerWriter = Action(
            schemaVersion = WireVersion.parse("99.0"),
            providerId = ProviderId.PHONE,
            payload = ActionPayload.Phone("+1"),
        )
        val r = dispatcher(handlers = mapOf(ProviderId.PHONE to handler(DispatchResult.Ok)))
            .dispatch(newerWriter)
        assertTrue("expected Ok, got $r", r is DispatchResult.Ok)
    }

    @Test
    fun higherMinReaderVersion_returnsFailure() = runTest {
        val needsNewerReader = Action(
            schemaVersion = WireVersion.parse("2.0"),
            minReaderVersion = WireVersion.parse("2.0"),
            minWriterVersion = WireVersion.parse("2.0"),
            providerId = ProviderId.PHONE,
            payload = ActionPayload.Phone("+1"),
        )
        val r = dispatcher(handlers = mapOf(ProviderId.PHONE to handler(DispatchResult.Ok)))
            .dispatch(needsNewerReader)
        assertTrue(r is DispatchResult.Failure)
        assertTrue((r as DispatchResult.Failure).reason.contains("unsupported wire version"))
    }

    // -- contracts/diagnostics-events-v2.md Emission rules ---------------

    @Test
    fun emitsActionDispatchedExactlyOnce_perTopLevel_evenWithFallback() = runTest {
        val router = mockk<EventRouter>(relaxed = true)
        val emissions = mutableListOf<ProjectEvent>()
        every { router.emit(any()) } answers { emissions += firstArg<ProjectEvent>() }

        val action = Action(
            providerId = ProviderId.WHATSAPP,
            payload = ActionPayload.WhatsAppMessage("alice"),
            fallback = anyAction,  // recurses once
        )
        dispatcher(
            handlers = mapOf(
                ProviderId.WHATSAPP to handler(DispatchResult.Failure("x")),
                ProviderId.PHONE    to handler(DispatchResult.Ok),
            ),
            eventRouter = router,
        ).dispatch(action)

        assertEquals("expected exactly 1 ActionDispatched event, got ${emissions.size}", 1, emissions.size)
        val emitted = emissions.first() as ProjectEvent.ActionDispatched
        // providerId on the event = top-level provider, not the one that finally succeeded
        assertEquals(ProviderId.WHATSAPP, emitted.providerId)
        assertTrue(emitted.fallbackUsed)
        assertEquals(ProjectEvent.ActionDispatched.ResultKind.Ok, emitted.resultKind)
    }
}
