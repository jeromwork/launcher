package family.push.api

import kotlin.time.Duration

/**
 * T024 — Port для running [PushHandler] в background job с timeout + retry.
 * Per spec 019 FR-074, FR-078, data-model.md §BackgroundDispatcher.
 *
 * Receiver flow: [LauncherFirebaseMessagingService] parses payload → registry
 * lookup → BackgroundDispatcher.dispatch(taskName, eventType.handlerTimeout) { handler.handle(...) }.
 *
 * Default implementation на Android: WorkManagerBackgroundDispatcher — WorkManager
 * OneTimeWorkRequest с input data (taskName + timeout). Per plan §G6: WorkManager
 * **по умолчанию**, не fallback. Foundation для 9 consumers с varied payloads
 * (config 50KB → photo 5MB).
 *
 * Future iOS adapter: BGTaskBackgroundDispatcher (BGTaskScheduler).
 *
 * Test adapter: [family.push.fakes.FakeBackgroundDispatcher] — synchronous execution
 * без WorkManager.
 */
interface BackgroundDispatcher {

    /**
     * Enqueues [block] для background execution с заданным timeout/retry.
     *
     *  • [taskName] — unique identifier (WorkManager UniqueWork). Multiple dispatches
     *    с тем же taskName collapse в одну job (REPLACE policy) — обеспечивает
     *    debounce per FR-044.
     *  • [timeout] — per-event-type budget (см. [EventType.handlerTimeout]).
     *  • [retryStrategy] — default [RetryStrategy.ExponentialBackoff] (3 attempts,
     *    1s initial).
     *  • [block] — suspend function executing the actual work.
     */
    suspend fun dispatch(
        taskName: String,
        timeout: Duration,
        retryStrategy: RetryStrategy = RetryStrategy.ExponentialBackoff(),
        block: suspend () -> Unit,
    )
}
