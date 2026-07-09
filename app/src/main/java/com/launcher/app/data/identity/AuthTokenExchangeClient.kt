package com.launcher.app.data.identity

import family.push.api.IdTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * TASK-119 (2026-07-09) — replaces the pre-2026-07-09 [InitClaimClient].
 *
 * Client for `POST /auth/exchange` on the auth-worker
 * (formerly the identity-worker). The endpoint exchanges a Firebase ID token
 * for our own HS256-signed JWT whose `sub` claim IS the caller's stableId,
 * without any custom-claim propagation window.
 *
 * **Contract**:
 *   POST /auth/exchange
 *   Authorization: Bearer <Firebase ID token>
 *   → 200 { "token": "<our JWT>", "stableId": "<uuid>", "expiresAt": <epoch ms> }
 *
 * **Why this replaces `/init-claim`**: the old flow relied on Firebase Auth
 * custom claims (`setCustomAttributes` → propagation → next `getIdToken(true)`
 * carries the claim). Propagation could take seconds to minutes, and the
 * downstream backup Worker's soft-fallback fetch/delete then couldn't match
 * blobs stored under `authed.stableId` that changed value across the
 * propagation boundary — visible in TASK-119 diagnostic runs as a
 * `HTTP 403 STABLE_ID_MISMATCH` on the returning-user probe. Moving identity
 * issuance out to a dedicated auth endpoint returns the stableId + our JWT
 * synchronously in one round-trip; no race, no retry loop.
 *
 * **Result vocabulary** — narrow on purpose:
 *  - [ExchangeResult.Success] with the issued token / stableId / expiresAt.
 *  - [ExchangeResult.AuthExpired] — null Firebase token, 401, or 403 from
 *    the auth-worker (caller drives re-Sign-In).
 *  - [ExchangeResult.NetworkUnavailable] — IO failure / 5xx / unknown.
 *  - [ExchangeResult.MalformedResponse] — 200 without the required JSON
 *    fields (defensive — the contract guarantees them).
 */
class AuthTokenExchangeClient(
    private val workerBaseUrl: String,
    private val idTokenProvider: IdTokenProvider,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MS,
) {

    suspend fun exchange(): ExchangeResult = withContext(Dispatchers.IO) {
        val firebaseToken = idTokenProvider.currentIdToken()
            ?: return@withContext ExchangeResult.AuthExpired

        val conn = openConnection("$workerBaseUrl/auth/exchange")
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $firebaseToken")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // Empty body — the endpoint only needs the Firebase JWT in the
            // Authorization header. Body reserved for future fields.
            conn.outputStream.use { it.write("{}".encodeToByteArray()) }
            val code = conn.responseCode
            return@withContext when (code) {
                in 200..299 -> readSuccessOrMalformed(conn)
                401, 403 -> {
                    val errBody = try {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    } catch (_: IOException) {
                        ""
                    }
                    android.util.Log.w(
                        "AuthTokenExchange",
                        "HTTP $code from auth-worker; body=$errBody",
                    )
                    ExchangeResult.AuthExpired
                }
                in 500..599 -> ExchangeResult.NetworkUnavailable
                else -> ExchangeResult.NetworkUnavailable
            }
        } catch (e: IOException) {
            ExchangeResult.NetworkUnavailable
        } finally {
            conn.disconnect()
        }
    }

    private fun readSuccessOrMalformed(conn: HttpURLConnection): ExchangeResult {
        val text = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            return ExchangeResult.MalformedResponse
        }
        // Minimal JSON extraction — same rationale as InitClaimClient predecessor:
        // avoid pulling in a JSON library for three well-defined fields.
        val token = extractString(text, "token") ?: return ExchangeResult.MalformedResponse
        val stableId = extractString(text, "stableId") ?: return ExchangeResult.MalformedResponse
        val expiresAt = extractLong(text, "expiresAt") ?: return ExchangeResult.MalformedResponse
        if (token.isEmpty() || stableId.isEmpty()) return ExchangeResult.MalformedResponse
        return ExchangeResult.Success(token = token, stableId = stableId, expiresAt = expiresAt)
    }

    private fun extractString(text: String, key: String): String? {
        val marker = "\"$key\""
        val keyIdx = text.indexOf(marker)
        if (keyIdx < 0) return null
        val colon = text.indexOf(':', keyIdx + marker.length)
        if (colon < 0) return null
        val firstQuote = text.indexOf('"', colon + 1)
        if (firstQuote < 0) return null
        val secondQuote = text.indexOf('"', firstQuote + 1)
        if (secondQuote < 0) return null
        return text.substring(firstQuote + 1, secondQuote)
    }

    private fun extractLong(text: String, key: String): Long? {
        val marker = "\"$key\""
        val keyIdx = text.indexOf(marker)
        if (keyIdx < 0) return null
        val colon = text.indexOf(':', keyIdx + marker.length)
        if (colon < 0) return null
        // Number ends at the next `,` or `}` (JSON object terminator).
        val start = colon + 1
        var end = start
        while (end < text.length && text[end] !in ",}") end++
        return text.substring(start, end).trim().toLongOrNull()
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMillis
        conn.readTimeout = readTimeoutMillis
        conn.useCaches = false
        return conn
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS: Int = 10_000
        const val DEFAULT_READ_TIMEOUT_MS: Int = 15_000
    }
}

sealed class ExchangeResult {
    data class Success(
        val token: String,
        val stableId: String,
        val expiresAt: Long,
    ) : ExchangeResult()
    object AuthExpired : ExchangeResult()
    object NetworkUnavailable : ExchangeResult()
    object MalformedResponse : ExchangeResult()
}
