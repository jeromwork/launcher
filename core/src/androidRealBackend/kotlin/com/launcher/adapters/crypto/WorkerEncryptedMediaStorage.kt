package com.launcher.adapters.crypto

import android.util.Log
import com.launcher.adapters.push.FirebaseTokenSupplier
import com.launcher.adapters.push.WorkerPushSender
import family.crypto.exception.CryptoException
import family.pairing.api.EncryptedMediaStorage
import java.net.HttpURLConnection
import java.net.URL
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * [EncryptedMediaStorage] implementation that proxies through the Cloudflare
 * Worker, which itself signs S3 v4 requests to Backblaze B2 (spec 011
 * FR-030..033, server-roadmap SRV-CRYPTO-001).
 *
 * TASK-141 — the upload/download/exists surface (and the EncryptedEnvelope wire
 * format they carried) was never wired into production; only a debug smoke
 * screen used it. It was removed. The live consumer is
 * FirestoreLinkRegistry.revoke() (FR-043), which needs only list + delete.
 *
 * TASK-51 Phase 6 — Outcome<T, CryptoError> → throws CryptoException; imports
 * family.pairing.api.*. Universal logging contract (FR-017) — operation /
 * exceptionClass / messageHash; никаких raw bytes / link-ids / uuids в логах.
 */
@OptIn(ExperimentalUuidApi::class)
class WorkerEncryptedMediaStorage(
    private val tokenSupplier: FirebaseTokenSupplier,
    private val baseUrl: String = WorkerPushSender.WORKER_BASE_URL,
) : EncryptedMediaStorage {

    override suspend fun delete(
        linkId: String,
        uuid: Uuid,
    ) = withContext(Dispatchers.IO) {
        withCryptoLogging("delete") {
            val idToken = tokenSupplier.currentIdToken()
                ?: throw CryptoException.KeyStoreException("no auth token")
            val conn = openConn(linkId, uuid, idToken, method = "DELETE")
            try {
                val code = conn.responseCode
                when {
                    code == 404 || code in 200..299 -> Unit
                    code == 401 || code == 403 -> throw CryptoException.KeyStoreException("delete HTTP $code")
                    else -> throw CryptoException.SerializationException("delete HTTP $code: ${readErrorBody(conn)}")
                }
            } finally {
                conn.disconnect()
            }
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
        } catch (ce: CancellationException) {
            throw ce
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
    ): HttpURLConnection {
        val url = URL("$baseUrl/blobs/${urlEncode(linkId)}/${urlEncode(uuid.toString())}")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $idToken")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readErrorBody(conn: HttpURLConnection): String? = try {
        (conn.errorStream ?: conn.inputStream).bufferedReader().use { it.readText() }
    } catch (_: Throwable) {
        null
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private inline fun <T> withCryptoLogging(
        operation: String,
        block: () -> T,
    ): T = try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: CryptoException) {
        Log.w(
            LOG_TAG,
            "operation=$operation exceptionClass=${e.javaClass.simpleName} " +
                "messageHash=${e.message?.hashCode()}",
        )
        throw e
    } catch (e: Throwable) {
        Log.w(
            LOG_TAG,
            "operation=$operation exceptionClass=${e.javaClass.simpleName} " +
                "messageHash=${e.message?.hashCode()}",
        )
        throw CryptoException.SerializationException("unexpected $operation failure", e)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS: Int = 10_000
        private const val READ_TIMEOUT_MS: Int = 60_000

        private const val LOG_TAG: String = "cryptokit"
    }
}
