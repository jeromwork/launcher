package family.push

import family.wire.WireVersion

import family.push.api.EventType
import family.push.api.IdTokenProvider
import family.push.api.Outcome
import family.push.api.PushTriggerError
import family.push.api.TargetScope
import family.push.impl.DefaultPushTrigger
import family.push.impl.FixedIdempotencyKeyGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * Tests для [DefaultPushTrigger] using Ktor MockEngine. Verifies response
 * mapping per FR-076 (PushTriggerError variants).
 */
class DefaultPushTriggerTest {

    private val fixedIdToken = "test-id-token"
    private val tokenProvider = object : IdTokenProvider {
        override suspend fun currentIdToken(): String? = fixedIdToken
    }

    private fun client(
        status: HttpStatusCode,
        body: String = "{\"ok\":true}",
    ): HttpClient = HttpClient(MockEngine { _ ->
        respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf("Content-Type", "application/json"),
        )
    }) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Test
    fun trigger_200_returnsSuccess() = runTest {
        val trigger = DefaultPushTrigger(
            httpClient = client(HttpStatusCode.OK),
            workerBaseUrl = "https://example.test/push",
            idTokenProvider = tokenProvider,
            idempotencyKeyGenerator = FixedIdempotencyKeyGenerator("uuid-1"),
        )
        val outcome = trigger.trigger(
            eventType = EventType.ConfigUpdated,
            targetScope = TargetScope.OwnAndGrants,
            ownerUid = "owner-1",
            payload = mapOf("configName" to "main"),
        )
        assertEquals(Outcome.Success(Unit), outcome)
    }

    @Test
    fun trigger_401_returnsUnauthorized() = runTest {
        val trigger = DefaultPushTrigger(
            httpClient = client(HttpStatusCode.Unauthorized, body = "invalid token"),
            workerBaseUrl = "https://example.test/push",
            idTokenProvider = tokenProvider,
            idempotencyKeyGenerator = FixedIdempotencyKeyGenerator("uuid-1"),
        )
        val outcome = trigger.trigger(
            EventType.ConfigUpdated,
            TargetScope.OwnDevices,
            "owner-1",
        )
        assertEquals(Outcome.Failure(PushTriggerError.Unauthorized), outcome)
    }

    @Test
    fun trigger_429_returnsRateLimited() = runTest {
        val trigger = DefaultPushTrigger(
            httpClient = client(HttpStatusCode.TooManyRequests, body = "limit"),
            workerBaseUrl = "https://example.test/push",
            idTokenProvider = tokenProvider,
            idempotencyKeyGenerator = FixedIdempotencyKeyGenerator("uuid-1"),
        )
        val outcome = trigger.trigger(
            EventType.ConfigUpdated,
            TargetScope.OwnDevices,
            "owner-1",
        )
        assertEquals(Outcome.Failure(PushTriggerError.RateLimited), outcome)
    }

    @Test
    fun trigger_500_returnsBackend() = runTest {
        val trigger = DefaultPushTrigger(
            httpClient = client(HttpStatusCode.InternalServerError, body = "server error"),
            workerBaseUrl = "https://example.test/push",
            idTokenProvider = tokenProvider,
            idempotencyKeyGenerator = FixedIdempotencyKeyGenerator("uuid-1"),
        )
        val outcome = trigger.trigger(
            EventType.ConfigUpdated,
            TargetScope.OwnDevices,
            "owner-1",
        )
        assertTrue(outcome is Outcome.Failure)
        assertTrue(outcome.error is PushTriggerError.Backend)
    }

    @Test
    fun trigger_noIdToken_returnsUnauthorized_withoutHttpCall() = runTest {
        val noTokenProvider = object : IdTokenProvider {
            override suspend fun currentIdToken(): String? = null
        }
        val trigger = DefaultPushTrigger(
            httpClient = client(HttpStatusCode.OK),
            workerBaseUrl = "https://example.test/push",
            idTokenProvider = noTokenProvider,
            idempotencyKeyGenerator = FixedIdempotencyKeyGenerator("uuid-1"),
        )
        val outcome = trigger.trigger(
            EventType.ConfigUpdated,
            TargetScope.OwnDevices,
            "owner-1",
        )
        assertEquals(Outcome.Failure(PushTriggerError.Unauthorized), outcome)
    }
}
