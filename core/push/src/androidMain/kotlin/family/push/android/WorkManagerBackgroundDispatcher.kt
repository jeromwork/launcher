package family.push.android

import family.push.api.BackgroundDispatcher
import family.push.api.RetryStrategy
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/**
 * T112 — [BackgroundDispatcher] impl. Per spec 019 FR-074, FR-078.
 *
 * **Honest naming caveat**: the plan called this `WorkManagerBackgroundDispatcher`
 * envisioning OneTimeWorkRequest-backed dispatch. But [BackgroundDispatcher.dispatch]
 * signature takes a `block: suspend () -> Unit` lambda — lambdas can't be
 * serialized into WorkManager `Data`. So this impl runs the block inline with
 * [withTimeout] + retry loop. The class keeps the planned name for plan-trace
 * (T112), and the `taskName` parameter is preserved for future migration к
 * proper WorkManager dispatch when a long-running event type (e.g. photo download)
 * forces redesign.
 *
 * Current behaviour (sufficient для config-updated, 30s budget):
 *   1. `withTimeout(timeout) { block() }` — bounds execution.
 *   2. On exception per [RetryStrategy] — retry с backoff (synchronous via
 *      [delay], NOT scheduled через WorkManager).
 *   3. On final failure — propagate to caller (LauncherFirebaseMessagingService
 *      catches и logs per FR-075).
 *
 * **Why this works for F-5c MVP**: config download is short (~ единицы секунд).
 * FCM data-message receiver itself has ~10s budget, and 3 retries × 1s+2s+4s + actual
 * work fits comfortably. Photo-download (V-3 album, 5min handlerTimeout) WILL force
 * proper WorkManager redesign — that's the trigger for refactor.
 *
 * Tracked as inline TODO для when LiveQuery / long-running events arrive:
 * see TODO-ARCH-020 (V-2 hybrid architecture).
 */
class WorkManagerBackgroundDispatcher : BackgroundDispatcher {

    override suspend fun dispatch(
        taskName: String,
        timeout: Duration,
        retryStrategy: RetryStrategy,
        block: suspend () -> Unit,
    ) {
        val maxAttempts = when (retryStrategy) {
            RetryStrategy.NoRetry -> 1
            is RetryStrategy.ExponentialBackoff -> retryStrategy.maxAttempts
            is RetryStrategy.FixedDelay -> retryStrategy.maxAttempts
        }

        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxAttempts) {
            attempt++
            try {
                withTimeout(timeout) { block() }
                return  // success.
            } catch (te: TimeoutCancellationException) {
                lastError = te
                // Timeout — do NOT retry, behaviour is "best effort, fall through".
                // Receiver eventually catches up via pull-on-app-open (FR-038).
                break
            } catch (t: Throwable) {
                lastError = t
                if (attempt >= maxAttempts) break
                val backoff = computeBackoff(retryStrategy, attempt)
                delay(backoff)
            }
        }
        // Final failure — re-throw so caller can log (per FR-075).
        if (lastError != null) throw lastError
    }

    private fun computeBackoff(strategy: RetryStrategy, attempt: Int): Long =
        when (strategy) {
            RetryStrategy.NoRetry -> 0L
            is RetryStrategy.ExponentialBackoff ->
                strategy.initialDelay.inWholeMilliseconds * (1L shl (attempt - 1))
            is RetryStrategy.FixedDelay -> strategy.delay.inWholeMilliseconds
        }
}
