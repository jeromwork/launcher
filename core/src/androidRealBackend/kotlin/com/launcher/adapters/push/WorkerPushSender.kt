package com.launcher.adapters.push

import com.launcher.api.push.PushError
import com.launcher.api.push.PushSender
import com.launcher.api.push.PushType
import com.launcher.api.result.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * [PushSender] that posts to the Cloudflare Worker `/notify` endpoint
 * (FR-036, contracts/worker-notify.md). The Worker validates the Firebase
 * ID-token, checks `uid == links/{linkId}.adminId`, and forwards an FCM
 * data-message on topic `link-{linkId}`.
 *
 * **Why plain HttpURLConnection** (instead of OkHttp / Ktor): the call
 * surface is one POST with a small JSON body, made <100×/day in MVP.
 * Adding a new HTTP client dep for this is over-engineering (CLAUDE.md
 * §4 — MVA). When a second consumer needs HTTP (admin API, analytics,
 * …) we'll introduce ktor-client across the realBackend source set.
 *
 * TODO(custom-domain, project-backlog TODO-ARCH-001): when migrating from
 * `*.workers.dev` to our own domain, [WORKER_BASE_URL] becomes an env-driven
 * BuildConfig field. The replacement is a single source change in :app's
 * `realBackend` flavor config — no client-code change.
 */
class WorkerPushSender(
    private val tokenSupplier: FirebaseTokenSupplier,
    private val baseUrl: String = WORKER_BASE_URL,
) : PushSender {

    override suspend fun notify(
        linkId: String,
        type: PushType,
        extra: JsonObject?,
    ): Outcome<Unit, PushError> = withContext(Dispatchers.IO) {
        val idToken = tokenSupplier.currentIdToken()
            ?: return@withContext Outcome.Failure(PushError.Unauthorized)

        val body = buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("linkId", JsonPrimitive(linkId))
            put("type", JsonPrimitive(type.wireValue))
            if (extra != null) put("payload", extra)
        }
        val bodyBytes = Json.encodeToString(JsonObject.serializer(), body).toByteArray(StandardCharsets.UTF_8)

        val conn = (URL("$baseUrl/notify").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $idToken")
            setRequestProperty("Accept", "application/json")
        }

        try {
            conn.outputStream.use { it.write(bodyBytes) }
            val code = conn.responseCode
            when {
                code in 200..299 -> Outcome.Success(Unit)
                code == 401 || code == 403 -> Outcome.Failure(PushError.Unauthorized)
                code == 429 -> Outcome.Failure(PushError.RateLimited)
                code in 500..599 -> {
                    val msg = readErrorBody(conn) ?: "server error"
                    Outcome.Failure(PushError.WorkerError(code, msg))
                }
                else -> Outcome.Failure(PushError.Unknown("HTTP $code"))
            }
        } catch (e: java.io.IOException) {
            Outcome.Failure(PushError.NetworkUnavailable)
        } catch (e: Throwable) {
            Outcome.Failure(PushError.Unknown(e.message ?: e::class.simpleName ?: "unknown"))
        } finally {
            conn.disconnect()
        }
    }

    private fun readErrorBody(conn: HttpURLConnection): String? = try {
        (conn.errorStream ?: conn.inputStream).bufferedReader().use { it.readText() }
    } catch (_: Throwable) {
        null
    }

    companion object {
        // TODO(custom-domain): move to BuildConfig field driven by env var
        // when we migrate off *.workers.dev. See project-backlog TODO-ARCH-001
        // and push-worker/README.md §Migration to custom domain.
        const val WORKER_BASE_URL: String = "https://launcher-push.jeromwork.workers.dev"

        private const val CONNECT_TIMEOUT_MS: Int = 10_000
        private const val READ_TIMEOUT_MS: Int = 15_000
    }
}
