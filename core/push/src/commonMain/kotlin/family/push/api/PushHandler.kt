package family.push.api

/**
 * T021 — Public port для **receiving** push payload и executing event-specific
 * logic. Per spec 019 FR-023, data-model.md §PushHandler.
 *
 * Каждый event type имеет один (или ноль) PushHandler. Registry lookup
 * происходит в [LauncherFirebaseMessagingService] → [PushHandlerRegistry.handlerFor]
 * → dispatch через [BackgroundDispatcher.dispatch].
 *
 * Implementation example: ConfigUpdatedHandler (T121, app module) — invokes
 * ConfigSaver.loadOwn(configName) или loadForOther(ownerUid, configName)
 * based on payload.ownerUid vs currentUid.
 *
 * Idempotency contract: FCM может deliver duplicates. Handler MUST be idempotent
 * (debounce по payload.triggerId per FR-044, SC-006).
 */
interface PushHandler {

    /**
     * Process received push payload. Invoked внутри coroutine, scoped к
     * [BackgroundDispatcher] job lifetime (timeout от [EventType.handlerTimeout]).
     *
     *  • Любая exception → caller (BackgroundDispatcher) обрабатывает per
     *    [RetryStrategy]. Default — exponential backoff 3 attempts.
     *  • Handler не должен holds long resources beyond payload processing.
     */
    suspend fun handle(payload: PushPayload)
}
