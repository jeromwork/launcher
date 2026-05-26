package com.launcher.adapters.crypto

import com.launcher.adapters.push.FirebaseTokenSupplier
import com.launcher.adapters.push.WorkerPushSender
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.EncryptedEnvelope
import com.launcher.api.crypto.EncryptedMediaStorage
import com.launcher.api.result.Outcome
import java.net.HttpURLConnection
import java.net.URL
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * [EncryptedMediaStorage] implementation that proxies through the Cloudflare
 * Worker, which itself signs S3 v4 requests to Backblaze B2 (spec 011
 * FR-030..033, server-roadmap SRV-CRYPTO-001).
 *
 * **Why Worker proxy instead of direct B2 SDK on Android**:
 * - Credentials (B2 keyID + applicationKey) **stay in Cloudflare secrets**,
 *   never on the device — even if device is rooted, secrets safe.
 * - Worker enforces link-membership authorization (admin OR managed) via
 *   Firestore lookup, same auth pipeline as `/notify` (FCM push).
 * - When we migrate to own backend (SRV-CRYPTO-001), only Worker endpoints
 *   change — Kotlin adapter stays.
 *
 * **Why HttpURLConnection** (not OkHttp / Ktor): same rationale as
 * [WorkerPushSender] — single-purpose HTTP client, ≤ a few requests per minute
 * in MVP, no need to introduce new transport dep (CLAUDE.md rule 4 — MVA).
 *
 * Endpoints (all under [baseUrl]):
 *   PUT    /blobs/{linkId}/{uuid}    upload — body = CBOR bytes
 *   GET    /blobs/{linkId}/{uuid}    download — returns CBOR bytes
 *   DELETE /blobs/{linkId}/{uuid}    delete (idempotent, 404→success)
 *   GET    /blobs/{linkId}/          list — returns {uuids: [...]} JSON
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)
class WorkerEncryptedMediaStorage(
    private val tokenSupplier: FirebaseTokenSupplier,
    private val baseUrl: String = WorkerPushSender.WORKER_BASE_URL,
    private val cbor: Cbor = Cbor { ignoreUnknownKeys = true },
) : EncryptedMediaStorage {

    override suspend fun upload(
        linkId: String,
        uuid: Uuid,
        envelope: EncryptedEnvelope,
    ): Outcome<Unit, CryptoError> = withContext(Dispatchers.IO) {
        val idToken = tokenSupplier.currentIdToken()
            ?: return@withContext Outcome.Failure(CryptoError.StorageFailure(IllegalStateException("no auth token")))
        val bytes = cbor.encodeToByteArray(envelope)
        val conn = openConn(linkId, uuid, idToken, method = "PUT", expectsBody = true).apply {
            setRequestProperty("Content-Type", "application/cbor")
            setRequestProperty("Content-Length", bytes.size.toString())
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            when {
                code in 200..299 -> Outcome.Success(Unit)
                code == 401 || code == 403 -> Outcome.Failure(CryptoError.StorageFailure(SecurityException("HTTP $code")))
                code == 413 -> Outcome.Failure(CryptoError.StorageFailure(IllegalArgumentException("blob too large")))
                else -> Outcome.Failure(CryptoError.StorageFailure(RuntimeException("HTTP $code: ${readErrorBody(conn)}")))
            }
        } catch (e: Throwable) {
            Outcome.Failure(CryptoError.StorageFailure(e))
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun download(
        linkId: String,
        uuid: Uuid,
    ): Outcome<EncryptedEnvelope, CryptoError> = withContext(Dispatchers.IO) {
        val idToken = tokenSupplier.currentIdToken()
            ?: return@withContext Outcome.Failure(CryptoError.StorageFailure(IllegalStateException("no auth token")))
        val conn = openConn(linkId, uuid, idToken, method = "GET", expectsBody = false)
        try {
            val code = conn.responseCode
            when {
                code == 404 -> Outcome.Failure(CryptoError.BlobMissing(uuid))
                code in 200..299 -> {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    try {
                        val envelope = cbor.decodeFromByteArray<EncryptedEnvelope>(bytes)
                        Outcome.Success(envelope)
                    } catch (e: Throwable) {
                        Outcome.Failure(CryptoError.MalformedEnvelope(uuid = uuid, cause = e))
                    }
                }
                code == 401 || code == 403 -> Outcome.Failure(CryptoError.StorageFailure(SecurityException("HTTP $code")))
                else -> Outcome.Failure(CryptoError.StorageFailure(RuntimeException("HTTP $code: ${readErrorBody(conn)}")))
            }
        } catch (e: Throwable) {
            Outcome.Failure(CryptoError.StorageFailure(e))
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun delete(
        linkId: String,
        uuid: Uuid,
    ): Outcome<Unit, CryptoError> = withContext(Dispatchers.IO) {
        val idToken = tokenSupplier.currentIdToken()
            ?: return@withContext Outcome.Failure(CryptoError.StorageFailure(IllegalStateException("no auth token")))
        val conn = openConn(linkId, uuid, idToken, method = "DELETE", expectsBody = false)
        try {
            val code = conn.responseCode
            when {
                code == 404 || code in 200..299 -> Outcome.Success(Unit)
                code == 401 || code == 403 -> Outcome.Failure(CryptoError.StorageFailure(SecurityException("HTTP $code")))
                else -> Outcome.Failure(CryptoError.StorageFailure(RuntimeException("HTTP $code: ${readErrorBody(conn)}")))
            }
        } catch (e: Throwable) {
            Outcome.Failure(CryptoError.StorageFailure(e))
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun exists(linkId: String, uuid: Uuid): Boolean = withContext(Dispatchers.IO) {
        // Cheapest check — issue HEAD; Worker doesn't have explicit HEAD route,
        // so мы делаем GET и просто проверяем code (отбрасываем body для 200).
        // В production использование `exists` редкое (только debug paths) — overhead приемлем.
        val idToken = tokenSupplier.currentIdToken() ?: return@withContext false
        val conn = openConn(linkId, uuid, idToken, method = "GET", expectsBody = false)
        try {
            conn.responseCode in 200..299
        } catch (_: Throwable) {
            false
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun list(linkId: String): List<Uuid> = withContext(Dispatchers.IO) {
        val idToken = tokenSupplier.currentIdToken() ?: return@withContext emptyList()
        val conn = (URL("$baseUrl/blobs/${urlEncode(linkId)}/").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $idToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) return@withContext emptyList()
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(text) as? JsonObject ?: return@withContext emptyList()
            val arr = json["uuids"]?.jsonArray ?: return@withContext emptyList()
            arr.mapNotNull { el ->
                runCatching { Uuid.parse(el.jsonPrimitive.content) }.getOrNull()
            }
        } catch (_: Throwable) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    private fun openConn(
        linkId: String,
        uuid: Uuid,
        idToken: String,
        method: String,
        expectsBody: Boolean,
    ): HttpURLConnection {
        val url = URL("$baseUrl/blobs/${urlEncode(linkId)}/${urlEncode(uuid.toString())}")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $idToken")
            setRequestProperty("Accept", if (expectsBody) "*/*" else "application/json")
        }
    }

    private fun readErrorBody(conn: HttpURLConnection): String? = try {
        (conn.errorStream ?: conn.inputStream).bufferedReader().use { it.readText() }
    } catch (_: Throwable) {
        null
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        private const val CONNECT_TIMEOUT_MS: Int = 10_000
        // Upload/download blobs может быть large (видео 25 MB max); read timeout
        // выше чем для push.
        private const val READ_TIMEOUT_MS: Int = 60_000
    }
}
