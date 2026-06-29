package com.launcher.app.data.recovery

import family.keys.api.BackupError
import family.keys.api.KdfParams
import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.impl.RecoveryBlobCodec
import family.push.api.IdTokenProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit test for [WorkerRecoveryKeyBackup] (T640, FR-010, plan §Test Strategy).
 *
 * **Why not MockWebServer**: would add OkHttp + MockWebServer as test
 * dependencies, ~3 MiB of JAR for what we exercise here (raw HTTP/1.1 with
 * a single in-flight request per test). Instead we use a bare-bones
 * [ServerSocket] HTTP/1.1 mock that accepts one request, records it, and
 * returns the configured response. Adequate for the surface the adapter
 * actually drives.
 *
 * **Why not com.sun.net.httpserver.HttpServer**: Android JVM unit-test
 * classpath does not include the `jdk.httpserver` module.
 *
 * Coverage: happy-path POST/GET/DELETE, 401/403/404/409/429/500/507 mapping,
 * Idempotency-Key presence, missing-token short-circuit, retry exhaustion.
 */
class WorkerRecoveryKeyBackupTest {

    private lateinit var mock: MockHttpServer

    @Before
    fun setUp() {
        mock = MockHttpServer()
        mock.start()
    }

    @After
    fun tearDown() {
        mock.close()
    }

    private fun adapter(
        token: String? = "fake-jwt",
        connectTimeoutMs: Int = 5_000,
        readTimeoutMs: Int = 5_000,
        maxAttempts: Int = 3,
    ): WorkerRecoveryKeyBackup = WorkerRecoveryKeyBackup(
        workerBaseUrl = mock.baseUrl,
        idTokenProvider = object : IdTokenProvider {
            override suspend fun currentIdToken(): String? = token
        },
        connectTimeoutMillis = connectTimeoutMs,
        readTimeoutMillis = readTimeoutMs,
        maxAttempts = maxAttempts,
        backoffMillis = { 1L },
    )

    private fun sampleBlob(): RecoveryKeyBackupBlob = RecoveryKeyBackupBlob(
        stableId = "00000000-0000-4000-8000-000000000001",
        salt = ByteArray(32) { 0x42 },
        kdfParams = KdfParams(),
        ciphertext = ByteArray(48) { 0x77 },
        nonce = ByteArray(24) { 0x11 },
        createdAt = Instant.parse("2026-06-29T00:00:00Z"),
    )

    @Test
    fun uploadHappyPathReturnsSuccess() = runTest {
        mock.respondAlwaysWith(200, "{\"status\":\"stored\"}")
        val result = adapter().uploadBlob("uid-1", sampleBlob())
        assertEquals(Outcome.Success(Unit), result)
        assertEquals(1, mock.requests.size)
        val req = mock.requests.single()
        assertEquals("POST", req.method)
        assertEquals("/backup", req.path)
        assertEquals("Bearer fake-jwt", req.headers["Authorization"])
        assertNotNull("Idempotency-Key MUST be set", req.headers["Idempotency-Key"])
        assertTrue("Idempotency-Key MUST be non-empty", req.headers["Idempotency-Key"]!!.isNotEmpty())
    }

    @Test
    fun uploadWithoutTokenReturnsAuthExpiredImmediately() = runTest {
        // Adapter must short-circuit before opening a connection.
        val result = adapter(token = null).uploadBlob("uid-1", sampleBlob())
        assertEquals(Outcome.Failure(BackupError.AuthExpired), result)
        assertTrue("No HTTP attempt MUST be made without a token", mock.requests.isEmpty())
    }

    @Test
    fun upload401IsAuthExpired() = runTest {
        mock.respondAlwaysWith(401, "{\"error\":\"INVALID_TOKEN\"}")
        val result = adapter().uploadBlob("uid-1", sampleBlob())
        assertEquals(Outcome.Failure(BackupError.AuthExpired), result)
    }

    @Test
    fun upload403IsAuthExpired() = runTest {
        mock.respondAlwaysWith(403, "{\"error\":\"STABLE_ID_MISMATCH\"}")
        val result = adapter().uploadBlob("uid-1", sampleBlob())
        assertEquals(Outcome.Failure(BackupError.AuthExpired), result)
    }

    @Test
    fun upload409IsConflict() = runTest {
        mock.respondAlwaysWith(409, "{\"error\":\"IDEMPOTENCY_CONFLICT\"}")
        val result = adapter().uploadBlob("uid-1", sampleBlob())
        assertEquals(Outcome.Failure(BackupError.Conflict), result)
    }

    @Test
    fun upload507IsServerQuotaExceeded() = runTest {
        mock.respondAlwaysWith(507, "{\"error\":\"R2_QUOTA_EXCEEDED\"}")
        val result = adapter().uploadBlob("uid-1", sampleBlob())
        val failure = result as Outcome.Failure<BackupError>
        assertTrue("Expected ServerQuotaExceeded, got ${failure.error}",
            failure.error is BackupError.ServerQuotaExceeded)
    }

    @Test
    fun upload500RetriesUpToMaxAttempts() = runTest {
        mock.respondAlwaysWith(500, "{\"error\":\"server\"}")
        val result = adapter(maxAttempts = 3).uploadBlob("uid-1", sampleBlob())
        val failure = result as Outcome.Failure<BackupError>
        assertTrue("Expected NetworkUnavailable, got ${failure.error}",
            failure.error is BackupError.NetworkUnavailable)
        assertEquals(3, mock.requests.size)
    }

    @Test
    fun upload429RetriesUpToMaxAttempts() = runTest {
        mock.respondAlwaysWith(429, "{\"error\":\"RATE_LIMITED\"}")
        val result = adapter(maxAttempts = 2).uploadBlob("uid-1", sampleBlob())
        val failure = result as Outcome.Failure<BackupError>
        assertTrue("Expected NetworkUnavailable, got ${failure.error}",
            failure.error is BackupError.NetworkUnavailable)
        assertEquals(2, mock.requests.size)
    }

    @Test
    fun upload400UnsupportedSchema() = runTest {
        mock.respondAlwaysWith(400, "{\"error\":\"UNSUPPORTED_SCHEMA\"}")
        val result = adapter().uploadBlob("uid-1", sampleBlob())
        val failure = result as Outcome.Failure<BackupError>
        assertTrue("Expected UnsupportedSchema, got ${failure.error}",
            failure.error is BackupError.UnsupportedSchema)
    }

    @Test
    fun upload400MalformedFallback() = runTest {
        mock.respondAlwaysWith(400, "{\"error\":\"MALFORMED_BODY\"}")
        val result = adapter().uploadBlob("uid-1", sampleBlob())
        assertEquals(Outcome.Failure(BackupError.Malformed), result)
    }

    @Test
    fun fetchHappyPathRoundtrips() = runTest {
        val blob = sampleBlob()
        mock.respondAlwaysWith(200, RecoveryBlobCodec.encode(blob))
        val result = adapter().fetchBlob("uid-1")
        val success = result as Outcome.Success<RecoveryKeyBackupBlob>
        assertEquals(blob, success.value)
        val req = mock.requests.single()
        assertEquals("GET", req.method)
        assertEquals("/backup/uid-1", req.path)
    }

    @Test
    fun fetch404IsNotFound() = runTest {
        mock.respondAlwaysWith(404, "{\"error\":\"NOT_FOUND\"}")
        val result = adapter().fetchBlob("uid-1")
        assertEquals(Outcome.Failure(BackupError.NotFound), result)
    }

    @Test
    fun deleteHappyPathReturnsSuccess() = runTest {
        mock.respondAlwaysWith(200, "")
        val result = adapter().deleteBlob("uid-1")
        assertEquals(Outcome.Success(Unit), result)
        assertEquals("DELETE", mock.requests.single().method)
    }

    @Test
    fun delete404IsIdempotentSuccess() = runTest {
        // The contract makes DELETE idempotent — 404 on a missing blob is OK.
        mock.respondAlwaysWith(404, "")
        val result = adapter().deleteBlob("uid-1")
        assertEquals(Outcome.Success(Unit), result)
    }
}

/**
 * Bare-bones HTTP/1.1 mock backed by a single [ServerSocket]. Accepts requests
 * sequentially, records them, and emits whatever response was configured.
 * Thread-bound — one I/O thread services all requests.
 */
private class MockHttpServer {

    private val server: ServerSocket = ServerSocket(0)
    val baseUrl: String = "http://127.0.0.1:${server.localPort}"
    val requests: MutableList<MockRequest> = CopyOnWriteArrayList()

    private val response = AtomicReference<MockResponse>(MockResponse(200, ""))

    @Volatile private var stopped = false
    private val acceptThread = Thread({ acceptLoop() }, "MockHttpServer-accept")

    fun start() {
        acceptThread.isDaemon = true
        acceptThread.start()
    }

    fun respondAlwaysWith(status: Int, body: String) {
        response.set(MockResponse(status, body))
    }

    fun close() {
        stopped = true
        try {
            server.close()
        } catch (_: IOException) {
            // ignore
        }
        acceptThread.join(1_000)
    }

    private fun acceptLoop() {
        while (!stopped) {
            try {
                val socket = server.accept()
                handle(socket)
            } catch (_: IOException) {
                if (!stopped) {
                    // unexpected, but we keep going to avoid stranding tests
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 3) return
            val method = parts[0]
            val path = parts[1]

            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val name = line.substring(0, colon).trim()
                    val value = line.substring(colon + 1).trim()
                    headers[name] = value
                    if (name.equals("Content-Length", ignoreCase = true)) {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            } else {
                ""
            }

            requests += MockRequest(method, path, headers, body)

            val resp = response.get()
            val out = PrintWriter(socket.getOutputStream(), false)
            val bodyBytes = resp.body.encodeToByteArray()
            out.print("HTTP/1.1 ${resp.status} ${reasonPhrase(resp.status)}\r\n")
            out.print("Content-Length: ${bodyBytes.size}\r\n")
            out.print("Connection: close\r\n")
            out.print("\r\n")
            out.flush()
            socket.getOutputStream().write(bodyBytes)
            socket.getOutputStream().flush()
        }
    }

    private fun reasonPhrase(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        507 -> "Insufficient Storage"
        else -> "Other"
    }
}

private data class MockRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

private data class MockResponse(
    val status: Int,
    val body: String,
)
