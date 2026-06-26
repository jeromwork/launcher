package com.launcher.adapters.crypto

import android.util.Log
import com.launcher.adapters.push.FirebaseTokenSupplier
import com.launcher.adapters.push.WorkerPushSender
import cryptokit.crypto.exception.CryptoException
import cryptokit.pairing.api.EncryptedEnvelope
import cryptokit.pairing.api.EncryptedMediaStorage
import java.net.HttpURLConnection
import java.net.URL
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * [EncryptedMediaStorage] implementation that proxies through the Cloudflare
 * Worker, which itself signs S3 v4 requests to Backblaze B2 (spec 011
 * FR-030..033, server-roadmap SRV-CRYPTO-001).
 *
 * TASK-51 Phase 6 — Outcome<T, CryptoError> → throws CryptoException; imports
 * cryptokit.pairing.api.*. Universal logging contract (FR-017) — operation /
 * exceptionClass / messageHash; никаких raw bytes / link-ids / uuids в логах.
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
    ) = withContext(Dispatchers.IO) {
        withCryptoLogging("upload") {
            val idToken = tokenSupplier.currentIdToken()
                ?: throw CryptoException.KeyStoreException("no auth token")
            val bytes = try {
                cbor.encodeToByteArray(envelope)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                throw CryptoException.SerializationException("CBOR encode failed", e)
            }
            val conn = openConn(linkId, uuid, idToken, method = "PUT", expectsBody = true).apply {
                setRequestProperty("Content-Type", "application/cbor")
                setRequestProperty("Content-Length", bytes.size.toString())
                doOutput = true
            }
            try {
                conn.outputStream.use { it.write(bytes) }
                val code = conn.responseCode
                when {
                    code in 200..299 -> Unit
                    code == 401 || code == 403 -> throw CryptoException.KeyStoreException("upload HTTP $code")
                    code == 413 -> throw CryptoException.SerializationException("blob too large (HTTP 413)")
                    else -> throw CryptoException.SerializationException("upload HTTP $code: ${readErrorBody(conn)}")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    override suspend fun download(
        linkId: String,
        uuid: Uuid,
    ): EncryptedEnvelope = withContext(Dispatchers.IO) {
        withCryptoLogging("download") {
            val idToken = tokenSupplier.currentIdToken()
                ?: throw CryptoException.KeyStoreException("no auth token")
            val conn = openConn(linkId, uuid, idToken, method = "GET", expectsBody = false)
            try {
                val code = conn.responseCode
                when {
                    code == 404 -> throw CryptoException.SerializationException("blob missing")
                    code in 200..299 -> {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        try {
                            cbor.decodeFromByteArray<EncryptedEnvelope>(bytes)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: Throwable) {
                            throw CryptoException.SerializationException("CBOR decode failed", e)
                        }
                    }
                    code == 401 || code == 403 ->
                        throw CryptoException.KeyStoreException("download HTTP $code")
                    else ->
                        throw CryptoException.SerializationException("download HTTP $code: ${readErrorBody(conn)}")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    override suspend fun delete(
        linkId: String,
        uuid: Uuid,
    ) = withContext(Dispatchers.IO) {
        withCryptoLogging("delete") {
            val idToken = tokenSupplier.currentIdToken()
                ?: throw CryptoException.KeyStoreException("no auth token")
            val conn = openConn(linkId, uuid, idToken, method = "DELETE", expectsBody = false)
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

    override suspend fun exists(linkId: String, uuid: Uuid): Boolean = withContext(Dispatchers.IO) {
        // Cheapest check — issue HEAD; Worker doesn't have explicit HEAD route,
        // so мы делаем GET и просто проверяем code (отбрасываем body для 200).
        val idToken = tokenSupplier.currentIdToken() ?: return@withContext false
        val conn = openConn(linkId, uuid, idToken, method = "GET", expectsBody = false)
        try {
            conn.responseCode in 200..299
        } catch (ce: CancellationException) {
            throw ce
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
        // Upload/download blobs может быть large (видео 25 MB max); read timeout
        // выше чем для push.
        private const val READ_TIMEOUT_MS: Int = 60_000

        private const val LOG_TAG: String = "cryptokit"
    }
}
