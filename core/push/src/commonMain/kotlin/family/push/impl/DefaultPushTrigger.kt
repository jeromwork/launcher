package family.push.impl

import family.push.api.EventType
import family.push.api.IdTokenProvider
import family.push.api.Outcome
import family.push.api.PushTrigger
import family.push.api.PushTriggerError
import family.push.api.TargetScope
import family.push.internal.PushTriggerRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException

/**
 * T054 — Orchestrator implementation of [PushTrigger]. Per spec 019 FR-025,
 * plan.md §Architecture.
 *
 * Sequence per call:
 *   1. Acquire Firebase ID-token via [idTokenProvider]. Null → fail-fast
 *      [PushTriggerError.Unauthorized] (caller's responsibility to ensure user
 *      signed-in before invoking).
 *   2. Generate idempotency-key (UUID v4) via [idempotencyKeyGenerator].
 *   3. POST {workerBaseUrl} с JSON body, `Authorization: Bearer <id-token>`,
 *      `Idempotency-Key: <uuid>`.
 *   4. Map response: 200/2xx → Success, 401 → Unauthorized, 429 → RateLimited,
 *      4xx → Backend(message), 5xx → Backend(message), IOException → NetworkFailure.
 *   5. NO client-side retry (FR-026) — Worker уже retries FCM 3×.
 *
 * Caller responsibility (fire-and-forget — FR-031):
 *   • Invoke внутри detached `applicationScope.launch { ... }`.
 *   • НЕ blocking primary user flow.
 *   • Log Outcome.Failure для observability, но не surface к user.
 *
 * Injection: [httpClient] should be configured by caller с Ktor JSON plugin
 * (ContentNegotiation + JSON serialization) — see [HttpPushTrigger.kt] на
 * androidMain для production wiring.
 */
class DefaultPushTrigger(
    private val httpClient: HttpClient,
    private val workerBaseUrl: String,
    private val idTokenProvider: IdTokenProvider,
    private val idempotencyKeyGenerator: IdempotencyKeyGenerator = RandomUuidV4IdempotencyKeyGenerator(),
) : PushTrigger {

    override suspend fun trigger(
        eventType: EventType,
        targetScope: TargetScope,
        ownerUid: String,
        payload: Map<String, String>,
    ): Outcome<Unit, PushTriggerError> {

        val idToken = idTokenProvider.currentIdToken()
            ?: return Outcome.Failure(PushTriggerError.Unauthorized)

        val request = PushTriggerRequest(
            eventType = eventType.wireValue,
            targetScope = targetScope.wireValue,
            ownerUid = ownerUid,
            payload = payload,
        )

        val idempotencyKey = idempotencyKeyGenerator.next()

        return try {
            val response: HttpResponse = httpClient.post(workerBaseUrl) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $idToken")
                    append("Idempotency-Key", idempotencyKey)
                }
                setBody(request)
            }
            mapResponse(response)
        } catch (ce: CancellationException) {
            throw ce
        } catch (re: ResponseException) {
            // Ktor uses expectSuccess plugin — non-2xx may throw. We mapping
            // status code defensively in оба пути.
            mapResponse(re.response)
        } catch (t: Throwable) {
            // Network failure (timeout, TLS, DNS, connection refused) →
            // NetworkFailure with message. Eventually consistent via pull-on-open.
            Outcome.Failure(PushTriggerError.NetworkFailure(t.message ?: "network failure"))
        }
    }

    private suspend fun mapResponse(response: HttpResponse): Outcome<Unit, PushTriggerError> {
        val status = response.status
        return when {
            status.value in 200..299 -> Outcome.Success(Unit)
            status == HttpStatusCode.Unauthorized -> Outcome.Failure(PushTriggerError.Unauthorized)
            status == HttpStatusCode.TooManyRequests -> Outcome.Failure(PushTriggerError.RateLimited)
            else -> {
                val message = runCatching { response.body<String>() }.getOrNull()
                    ?: "Worker returned ${status.value}"
                Outcome.Failure(PushTriggerError.Backend("HTTP ${status.value}: $message"))
            }
        }
    }
}
