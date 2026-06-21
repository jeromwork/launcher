package family.push.api

/**
 * T020 — Public port для **issuing** push trigger. Per spec 019 FR-020,
 * data-model.md §PushTrigger.
 *
 * Caller (e.g. ConfigSaver F-5b) invokes from detached coroutine after
 * successful state change (fire-and-forget — FR-031). Return [Outcome.Failure]
 * сигнализирует: push не достиг Worker'а. Caller MAY log, but MUST NOT block
 * primary flow on push success (eventually-consistent via pull-on-app-open,
 * FR-038).
 *
 * Implementations:
 *  • [family.push.impl.DefaultPushTrigger] — Ktor HTTP POST к Worker.
 *  • [family.push.impl.NullPushTrigger] — no-op for local-mode (CHK-DSS-007:
 *    device-self-sufficiency — devices работают без cloud).
 *  • [family.push.fakes.FakePushTrigger] — captures invocations для testing.
 */
interface PushTrigger {

    /**
     * Triggers push notification к recipient devices selected by [targetScope].
     *
     *  • [eventType] — sealed registry entry (e.g. [EventType.ConfigUpdated]).
     *  • [targetScope] — [TargetScope.OwnDevices] OR [TargetScope.OwnAndGrants].
     *  • [ownerUid] — Firebase UID of context owner (e.g. config owner). Per FR-005,
     *    Worker authorise rule decides who can trigger на этот ownerUid namespace.
     *  • [payload] — flat string map с event-type-specific fields. Каждый event type
     *    документирует ожидаемые ключи в [contracts/event-type-registry.md].
     *
     * Idempotency-Key generated internally per call (UUID v4). Worker dedupes via KV
     * (10-min TTL, FR-010). Two identical calls (caller-side retry) → single FCM dispatch.
     *
     * No client-side retry on failure (FR-026 — Worker уже retries FCM 3×, client retry
     * would amplify load and double-send risk).
     */
    suspend fun trigger(
        eventType: EventType,
        targetScope: TargetScope,
        ownerUid: String,
        payload: Map<String, String> = emptyMap(),
    ): Outcome<Unit, PushTriggerError>
}
