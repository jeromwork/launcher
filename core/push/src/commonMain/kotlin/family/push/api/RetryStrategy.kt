package family.push.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * T024 — Retry policy для [BackgroundDispatcher.dispatch]. Per data-model.md
 * §RetryStrategy.
 *
 * Каждый event type может выбрать свою стратегию. ConfigUpdated — exponential
 * backoff (transient network errors retryable). Future SOS — [NoRetry] (каждый
 * SOS unique; retry incorrect).
 */
sealed class RetryStrategy {

    /** No retry on failure. Use для events где retry would be incorrect
     *  (SOS, идентификация). */
    data object NoRetry : RetryStrategy()

    /** Exponential backoff: initialDelay × 2^attempt, max maxAttempts. Default. */
    data class ExponentialBackoff(
        val initialDelay: Duration = 1.seconds,
        val maxAttempts: Int = 3,
    ) : RetryStrategy()

    /** Fixed delay между retries. */
    data class FixedDelay(
        val delay: Duration,
        val maxAttempts: Int,
    ) : RetryStrategy()
}
