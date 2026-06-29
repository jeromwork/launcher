package com.launcher.app.data.identity

import family.push.api.IdTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for `POST /init-claim` on the identity Worker (T668, Q-M variant b).
 *
 * **Lifecycle**: called **once** by F-4 `GoogleSignInAuthAdapter` after the
 * first successful Sign-In, when `claims.stableId` is absent from the JWT.
 * The Worker generates a UUID v4, persists the `uid → stableId` binding,
 * and sets the Firebase custom claim. On every subsequent call this
 * endpoint returns the same `stableId` without re-allocation, so retry
 * after a transient failure is safe.
 *
 * **HTTP client** — `HttpURLConnection` (rule 4 MVA, same rationale as
 * [com.launcher.app.data.recovery.WorkerRecoveryKeyBackup]).
 *
 * **Bearer JWT** from [IdTokenProvider] (shared with backup adapter +
 * push subsystem).
 *
 * **Result vocabulary** — narrow on purpose:
 *  - [InitClaimResult.Success] with the issued stableId.
 *  - [InitClaimResult.AuthExpired] — null token, 401, or 403 from the
 *    Worker (caller drives re-Sign-In).
 *  - [InitClaimResult.NetworkUnavailable] — IO failure / 5xx / unknown.
 *  - [InitClaimResult.MalformedResponse] — 200 without `stableId` JSON
 *    field (defensive — the contract guarantees this field).
 */
class InitClaimClient(
    private val workerBaseUrl: String,
    private val idTokenProvider: IdTokenProvider,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MS,
) {

    suspend fun initClaim(uid: String): InitClaimResult = withContext(Dispatchers.IO) {
        require(uid.isNotEmpty()) { "uid MUST not be empty" }
        val token = idTokenProvider.currentIdToken()
            ?: return@withContext InitClaimResult.AuthExpired

        val conn = openConnection("$workerBaseUrl/init-claim")
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = "{\"uid\":\"${uid.replace("\"", "\\\"")}\"}"
            conn.outputStream.use { it.write(body.encodeToByteArray()) }
            val code = conn.responseCode
            return@withContext when (code) {
                in 200..299 -> readSuccessOrMalformed(conn)
                401, 403 -> InitClaimResult.AuthExpired
                in 500..599 -> InitClaimResult.NetworkUnavailable
                else -> InitClaimResult.NetworkUnavailable
            }
        } catch (e: IOException) {
            InitClaimResult.NetworkUnavailable
        } finally {
            conn.disconnect()
        }
    }

    private fun readSuccessOrMalformed(conn: HttpURLConnection): InitClaimResult {
        val text = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            return InitClaimResult.MalformedResponse
        }
        // Minimal JSON extraction — the contract always returns one of
        //   { "stableId": "<uuid>" }
        //   { "error": "<code>", ... }
        // We do a single-field substring scan rather than pulling in a full
        // JSON library. android.util.JsonReader exists in Android runtime
        // but not on JVM unit-test classpath, and org.json.JSONObject has
        // the same problem. Pattern `"stableId":"VALUE"` is unambiguous —
        // Worker output is always a flat JSON object with no nested escapes
        // inside the UUID v4 string.
        val key = "\"stableId\""
        val keyIdx = text.indexOf(key)
        if (keyIdx < 0) return InitClaimResult.MalformedResponse
        val colon = text.indexOf(':', keyIdx + key.length)
        if (colon < 0) return InitClaimResult.MalformedResponse
        val firstQuote = text.indexOf('"', colon + 1)
        if (firstQuote < 0) return InitClaimResult.MalformedResponse
        val secondQuote = text.indexOf('"', firstQuote + 1)
        if (secondQuote < 0) return InitClaimResult.MalformedResponse
        val value = text.substring(firstQuote + 1, secondQuote)
        return if (value.isEmpty()) InitClaimResult.MalformedResponse else InitClaimResult.Success(value)
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

sealed class InitClaimResult {
    data class Success(val stableId: String) : InitClaimResult()
    object AuthExpired : InitClaimResult()
    object NetworkUnavailable : InitClaimResult()
    object MalformedResponse : InitClaimResult()
}
