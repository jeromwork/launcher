package com.launcher.app.data.recovery

import family.keys.api.BackupError
import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackup
import family.keys.api.RecoveryKeyBackupBlob
import family.push.api.IdTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.pow

/**
 * HTTP adapter implementing [RecoveryKeyBackup] against the Cloudflare Worker
 * defined in [contracts/worker-api-v1.md] (T635, FR-010).
 *
 * **HTTP client** — `HttpURLConnection` (JDK built-in) rather than OkHttp.
 * The adapter speaks three trivial endpoints with no streaming, multipart, or
 * connection-reuse pressure; OkHttp would add ~830 KiB to the APK with no
 * observable benefit at this call volume (rule 4 MVA — do not add a library
 * for a single adapter).
 *
 * **Authentication** — `Authorization: Bearer <token>` from [IdTokenProvider]
 * (F-4 surface). If the token is `null` (user not signed in / refresh failed)
 * the call returns [BackupError.AuthExpired] without touching the network.
 *
 * **Retries** — POST and GET use 3 attempts with exponential backoff (100ms,
 * 400ms, 1600ms) on transient failures (network IOException, 5xx, 429 without
 * explicit Retry-After). DELETE retries up to 3 times on 5xx only —
 * idempotently safe per contract §4.3.
 *
 * **Idempotency-Key** — fresh UUID v4 per POST attempt round (NOT per retry
 * inside one round, so the Worker can dedup the same logical upload).
 *
 * **Mapping** of HTTP status → [BackupError]:
 *  - 200 / 201          → [Outcome.Success]
 *  - 400 + UNSUPPORTED  → [BackupError.UnsupportedSchema]
 *  - 400 (other)        → [BackupError.Malformed]
 *  - 401                → [BackupError.AuthExpired]
 *  - 403                → [BackupError.AuthExpired] (stableId mismatch — surface
 *                         as auth issue; caller drives re-sign-in)
 *  - 404                → [BackupError.NotFound]
 *  - 409                → [BackupError.Conflict]
 *  - 429                → [BackupError.NetworkUnavailable()] after retries
 *  - 5xx                → [BackupError.NetworkUnavailable()] after retries
 *  - 507                → [BackupError.ServerQuotaExceeded]
 *  - IO / timeout       → [BackupError.NetworkUnavailable()]
 *
 * **TODO(server-roadmap SRV-RECOVERY-001)**: when own server is in place,
 * replace BuildConfig URL with a domain constant and drop the
 * `*.workers.dev` placeholder.
 */
class WorkerRecoveryKeyBackup(
    private val workerBaseUrl: String,
    private val idTokenProvider: IdTokenProvider,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val backoffMillis: (attempt: Int) -> Long = ::defaultBackoff,
) : RecoveryKeyBackup {

    override suspend fun uploadBlob(
        uid: String,
        blob: RecoveryKeyBackupBlob
    ): Outcome<Unit, BackupError> = withContext(Dispatchers.IO) {
        require(uid.isNotEmpty()) { "uid (stableId) MUST not be empty" }
        val token = idTokenProvider.currentIdToken()
            ?: return@withContext Outcome.Failure(BackupError.AuthExpired)
        val body = RecoveryBlobJsonCodec.encode(blob)
        val idempotencyKey = UUID.randomUUID().toString()

        runWithRetry { attempt ->
            val conn = openConnection("$workerBaseUrl/backup")
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Idempotency-Key", idempotencyKey)
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.encodeToByteArray()) }
                val code = conn.responseCode
                when {
                    code in 200..299 -> RetryDecision.Done(Outcome.Success(Unit))
                    code == 400 -> RetryDecision.Done(classifyBadRequest(conn))
                    code == 401 -> RetryDecision.Done(Outcome.Failure(BackupError.AuthExpired))
                    code == 403 -> RetryDecision.Done(Outcome.Failure(BackupError.AuthExpired))
                    code == 409 -> RetryDecision.Done(Outcome.Failure(BackupError.Conflict))
                    code == 429 -> RetryDecision.Retry(Outcome.Failure(BackupError.NetworkUnavailable()))
                    code == 507 -> RetryDecision.Done(Outcome.Failure(BackupError.ServerQuotaExceeded()))
                    code in 500..599 -> RetryDecision.Retry(Outcome.Failure(BackupError.NetworkUnavailable()))
                    else -> RetryDecision.Done(Outcome.Failure(BackupError.NetworkUnavailable()))
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    override suspend fun fetchBlob(uid: String): Outcome<RecoveryKeyBackupBlob, BackupError> =
        withContext(Dispatchers.IO) {
            require(uid.isNotEmpty()) { "uid (stableId) MUST not be empty" }
            val token = idTokenProvider.currentIdToken()
                ?: return@withContext Outcome.Failure(BackupError.AuthExpired)

            runWithRetry { _ ->
                val conn = openConnection("$workerBaseUrl/backup/$uid")
                try {
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    val code = conn.responseCode
                    when {
                        code in 200..299 -> {
                            val json = conn.inputStream.bufferedReader().use { it.readText() }
                            RetryDecision.Done(RecoveryBlobJsonCodec.decode(json))
                        }
                        code == 401 -> RetryDecision.Done(Outcome.Failure(BackupError.AuthExpired))
                        code == 403 -> RetryDecision.Done(Outcome.Failure(BackupError.AuthExpired))
                        code == 404 -> RetryDecision.Done(Outcome.Failure(BackupError.NotFound))
                        code == 429 -> RetryDecision.Retry(Outcome.Failure(BackupError.NetworkUnavailable()))
                        code in 500..599 -> RetryDecision.Retry(Outcome.Failure(BackupError.NetworkUnavailable()))
                        else -> RetryDecision.Done(Outcome.Failure(BackupError.NetworkUnavailable()))
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }

    override suspend fun deleteBlob(uid: String): Outcome<Unit, BackupError> =
        withContext(Dispatchers.IO) {
            require(uid.isNotEmpty()) { "uid (stableId) MUST not be empty" }
            val token = idTokenProvider.currentIdToken()
                ?: return@withContext Outcome.Failure(BackupError.AuthExpired)

            runWithRetry { _ ->
                val conn = openConnection("$workerBaseUrl/backup/$uid")
                try {
                    conn.requestMethod = "DELETE"
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    val code = conn.responseCode
                    when {
                        code in 200..299 -> RetryDecision.Done(Outcome.Success(Unit))
                        code == 401 -> RetryDecision.Done(Outcome.Failure(BackupError.AuthExpired))
                        code == 403 -> RetryDecision.Done(Outcome.Failure(BackupError.AuthExpired))
                        code == 404 -> RetryDecision.Done(Outcome.Success(Unit))
                        code in 500..599 -> RetryDecision.Retry(Outcome.Failure(BackupError.NetworkUnavailable()))
                        else -> RetryDecision.Done(Outcome.Failure(BackupError.NetworkUnavailable()))
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }

    private suspend fun <T> runWithRetry(
        block: suspend (attempt: Int) -> RetryDecision<T>
    ): Outcome<T, BackupError> {
        var lastOutcome: Outcome<T, BackupError> = Outcome.Failure(BackupError.NetworkUnavailable())
        for (attempt in 1..maxAttempts) {
            val decision = try {
                block(attempt)
            } catch (e: IOException) {
                RetryDecision.Retry<T>(Outcome.Failure(BackupError.NetworkUnavailable()))
            }
            when (decision) {
                is RetryDecision.Done -> return decision.value
                is RetryDecision.Retry -> {
                    lastOutcome = decision.value
                    if (attempt < maxAttempts) {
                        delay(backoffMillis(attempt))
                    }
                }
            }
        }
        return lastOutcome
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMillis
        conn.readTimeout = readTimeoutMillis
        conn.useCaches = false
        return conn
    }

    private fun classifyBadRequest(conn: HttpURLConnection): Outcome<Unit, BackupError> {
        val body = try {
            conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } catch (_: IOException) {
            ""
        }
        return if (body.contains("UNSUPPORTED_SCHEMA")) {
            Outcome.Failure(BackupError.UnsupportedSchema(version = 0))
        } else {
            Outcome.Failure(BackupError.Malformed)
        }
    }

    private sealed class RetryDecision<T> {
        data class Done<T>(val value: Outcome<T, BackupError>) : RetryDecision<T>()
        data class Retry<T>(val value: Outcome<T, BackupError>) : RetryDecision<T>()
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS: Int = 10_000
        const val DEFAULT_READ_TIMEOUT_MS: Int = 15_000
        const val DEFAULT_MAX_ATTEMPTS: Int = 3
        // TASK-119 (2026-07-09): after moving to our-JWT auth, retry only covers
        // transient transport (5xx / 429 / IOException). Old 2s/4s/8s window
        // covered Firebase claim propagation — that failure mode no longer
        // exists on this path.
        private fun defaultBackoff(attempt: Int): Long = (500.0 * 2.0.pow(attempt - 1)).toLong()
    }
}
