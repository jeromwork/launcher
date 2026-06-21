package family.push.android

import family.push.api.IdTokenProvider
import family.push.api.PushTrigger
import family.push.impl.DefaultPushTrigger
import family.push.impl.IdempotencyKeyGenerator
import family.push.impl.RandomUuidV4IdempotencyKeyGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * T110 — Android factory wiring [DefaultPushTrigger] с CIO Ktor engine.
 *
 * Public API hides Ktor types (`HttpClient`), so callers can wire the
 * factory от Koin module без depending на Ktor classpath:
 * ```
 * single<PushTrigger> { HttpPushTrigger.create(idTokenProvider = get()) }
 * ```
 *
 * **Implementation note**: Phase 1+2 review uncovered that "HttpPushTrigger"
 * per plan T110 = wiring + Android-specific engine; the orchestration logic
 * itself is platform-agnostic and lives in [DefaultPushTrigger] (commonMain).
 * This factory is the seam where Android engine choice is made.
 */
object HttpPushTrigger {

    /** Convenience overload — uses default CIO engine + JSON. */
    fun create(idTokenProvider: IdTokenProvider): PushTrigger =
        createInternal(
            idTokenProvider = idTokenProvider,
            workerBaseUrl = WorkerBaseUrl.URL,
            idempotencyKeyGenerator = RandomUuidV4IdempotencyKeyGenerator(),
            httpClient = defaultHttpClient(),
        )

    /** Advanced overload — caller supplies own URL + generator. */
    fun create(
        idTokenProvider: IdTokenProvider,
        workerBaseUrl: String,
        idempotencyKeyGenerator: IdempotencyKeyGenerator,
    ): PushTrigger = createInternal(
        idTokenProvider = idTokenProvider,
        workerBaseUrl = workerBaseUrl,
        idempotencyKeyGenerator = idempotencyKeyGenerator,
        httpClient = defaultHttpClient(),
    )

    /**
     * Full advanced overload — exposes Ktor [HttpClient] для tests или custom
     * engines. Callers from realBackend must add Ktor к their own classpath.
     */
    fun createWithHttpClient(
        idTokenProvider: IdTokenProvider,
        workerBaseUrl: String,
        idempotencyKeyGenerator: IdempotencyKeyGenerator,
        httpClient: HttpClient,
    ): PushTrigger = createInternal(
        idTokenProvider = idTokenProvider,
        workerBaseUrl = workerBaseUrl,
        idempotencyKeyGenerator = idempotencyKeyGenerator,
        httpClient = httpClient,
    )

    private fun createInternal(
        idTokenProvider: IdTokenProvider,
        workerBaseUrl: String,
        idempotencyKeyGenerator: IdempotencyKeyGenerator,
        httpClient: HttpClient,
    ): PushTrigger = DefaultPushTrigger(
        httpClient = httpClient,
        workerBaseUrl = workerBaseUrl,
        idTokenProvider = idTokenProvider,
        idempotencyKeyGenerator = idempotencyKeyGenerator,
    )

    /** Default Ktor client configuration — CIO engine + JSON content negotiation. */
    private fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = false  // We map status codes manually в DefaultPushTrigger.
        install(ContentNegotiation) {
            json(
                Json {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000  // 10s — фоновое push trigger budget.
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }
}
