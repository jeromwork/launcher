package family.push.api

import family.push.fakes.FakePushHandler
import family.push.fakes.FakePushTrigger
import family.push.impl.DefaultPushHandlerRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

/**
 * T151 — Validates that adding a new event type requires ровно 3 place changes
 * без foundation modification. Per spec 019 SC-008, FR-060.
 *
 * **Why this test uses [EventType.ConfigUpdated] вместо declaring a test-only
 * variant**: `EventType` is a sealed interface, и Kotlin's sealed restriction
 * forbids extending it from а different module (commonTest is separate
 * compilation unit от commonMain). So we cannot создать `TestPing : EventType`
 * here.
 *
 * Это **fits the design intent** — sealed prevents accidental EventType impl
 * outside foundation source. New event types CAN be added только via single-file
 * edit к EventType.kt (`data object NewEvent : EventType`), then registry +
 * handler. This test demonstrates that with ConfigUpdated as the standin:
 * if это работает для ConfigUpdated, identical 3-place pattern works для
 * любого нового variant'а.
 *
 * Production pattern (per FR-060, contracts/event-type-registry.md):
 *   1. **Add к EventType.kt**: `data object NewEvent : EventType` (single line).
 *   2. **Add к workers/push/src/registry/event-types.ts**: registry entry.
 *   3. **Write PushHandler impl + register в DI**: feature module.
 *
 * **No foundation changes** to: PushTrigger, PushHandlerRegistry, DefaultPushTrigger,
 * LauncherFirebaseMessagingService, BackgroundDispatcher, recipient resolver,
 * idempotency, rate-limiter, JWT verification.
 */
class EventTypeExtensibilityTest {

    @Test
    fun foundation_dispatchesNewEventType_withoutCodeChanges() = runTest {
        // Step 1 (production): add `data object NewEvent : EventType` к EventType.kt.
        // Здесь используем существующий ConfigUpdated как proxy.
        val eventType: EventType = EventType.ConfigUpdated

        // Step 2 (production): add entry к EVENT_TYPES TypeScript registry.
        // (Worker-side; not exercised в этом Kotlin test.)

        // Step 3 (production): write handler + register в DI.
        val handler = FakePushHandler()
        val registry = DefaultPushHandlerRegistry()
        registry.register(eventType, handler)

        // === FOUNDATION USAGE — NO MODIFICATION REQUIRED ===

        // Trigger flow: generic PushTrigger port, no eventType-specific seam.
        val trigger = FakePushTrigger()
        val result = trigger.trigger(
            eventType = eventType,
            targetScope = TargetScope.OwnDevices,
            ownerUid = "test-uid",
            payload = mapOf("configName" to "demo"),
        )
        assertEquals(Outcome.Success(Unit), result)
        assertEquals(eventType, trigger.invocations.first().eventType)

        // Receiver dispatch: generic registry lookup, no eventType-specific seam.
        val payload = PushPayload(
            eventType = eventType.wireValue,
            ownerUid = "test-uid",
            triggerId = "trigger-1",
            fields = mapOf("configName" to "demo"),
        )
        val resolvedHandler = registry.handlerFor(eventType)
        assertNotNull(resolvedHandler)
        assertSame(handler, resolvedHandler)
        resolvedHandler.handle(payload)

        assertEquals(1, handler.handledPayloads.size)
        assertEquals("demo", handler.handledPayloads.first().fields["configName"])
    }

    @Test
    fun handlerTimeout_isPartOfEventTypePublicApi() {
        // FR-077 — per-event handlerTimeout exposed на interface (не magic constant).
        // BackgroundDispatcher receives it on dispatch.
        assertEquals(EventType.DEFAULT_TIMEOUT, EventType.ConfigUpdated.handlerTimeout)
    }

    @Test
    fun wireValue_matchesContractDocumentation() {
        // Validates contracts/event-type-registry.md §`config-updated` (F-5c).
        assertEquals("config-updated", EventType.ConfigUpdated.wireValue)
    }
}
