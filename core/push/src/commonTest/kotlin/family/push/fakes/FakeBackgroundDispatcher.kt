package family.push.fakes

import family.push.api.BackgroundDispatcher
import family.push.api.RetryStrategy
import kotlin.time.Duration

/**
 * T034 — Synchronous [BackgroundDispatcher] для unit tests. Executes block
 * inline (без WorkManager).
 *
 * Captures dispatched task names + last block result. Honors retry strategy
 * только counts attempts; не actually delays (test would hang).
 */
class FakeBackgroundDispatcher : BackgroundDispatcher {

    data class Dispatched(
        val taskName: String,
        val timeout: Duration,
        val retryStrategy: RetryStrategy,
        val attempts: Int,
        val lastError: Throwable?,
    )

    val dispatched: MutableList<Dispatched> = mutableListOf()

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

        var lastError: Throwable? = null
        var attempts = 0
        while (attempts < maxAttempts) {
            attempts++
            try {
                block()
                lastError = null
                break
            } catch (t: Throwable) {
                lastError = t
            }
        }

        dispatched += Dispatched(taskName, timeout, retryStrategy, attempts, lastError)
    }
}
