package com.launcher.app.data.identity

import family.push.api.IdTokenProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Unit test for [InitClaimClient] (Track C, FR-001 stableId provider-agnostic UUID).
 *
 * Uses the same bare-ServerSocket HTTP mock pattern as
 * `WorkerRecoveryKeyBackupTest` — no MockWebServer dependency.
 */
class InitClaimClientTest {

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

    private fun client(token: String? = "fake-jwt"): InitClaimClient = InitClaimClient(
        workerBaseUrl = mock.baseUrl,
        idTokenProvider = object : IdTokenProvider {
            override suspend fun currentIdToken(): String? = token
        },
        connectTimeoutMillis = 5_000,
        readTimeoutMillis = 5_000,
    )

    @Test
    fun happyPathReturnsSuccessWithStableId() = runTest {
        mock.respondAlwaysWith(200, "{\"stableId\":\"11111111-1111-4111-8111-111111111111\"}")
        val result = client().initClaim("uid-1")
        assertTrue("Expected Success, got $result", result is InitClaimResult.Success)
        assertEquals(
            "11111111-1111-4111-8111-111111111111",
            (result as InitClaimResult.Success).stableId
        )
        val req = mock.requests.single()
        assertEquals("POST", req.method)
        assertEquals("/init-claim", req.path)
        assertEquals("Bearer fake-jwt", req.headers["Authorization"])
        assertTrue(
            "Body MUST contain uid: ${req.body}",
            req.body.contains("\"uid\":\"uid-1\"")
        )
    }

    @Test
    fun nullTokenReturnsAuthExpiredImmediately() = runTest {
        val result = client(token = null).initClaim("uid-1")
        assertEquals(InitClaimResult.AuthExpired, result)
        assertTrue("No HTTP attempt MUST be made", mock.requests.isEmpty())
    }

    @Test
    fun http401ReturnsAuthExpired() = runTest {
        mock.respondAlwaysWith(401, "{\"error\":\"INVALID_TOKEN\"}")
        val result = client().initClaim("uid-1")
        assertEquals(InitClaimResult.AuthExpired, result)
    }

    @Test
    fun http403ReturnsAuthExpired() = runTest {
        mock.respondAlwaysWith(403, "{\"error\":\"UID_MISMATCH\"}")
        val result = client().initClaim("uid-1")
        assertEquals(InitClaimResult.AuthExpired, result)
    }

    @Test
    fun http500ReturnsNetworkUnavailable() = runTest {
        mock.respondAlwaysWith(500, "{\"error\":\"INTERNAL\"}")
        val result = client().initClaim("uid-1")
        assertEquals(InitClaimResult.NetworkUnavailable, result)
    }

    @Test
    fun emptyStableIdInResponseReturnsMalformed() = runTest {
        mock.respondAlwaysWith(200, "{\"stableId\":\"\"}")
        val result = client().initClaim("uid-1")
        assertEquals(InitClaimResult.MalformedResponse, result)
    }

    @Test
    fun missingStableIdFieldReturnsMalformed() = runTest {
        mock.respondAlwaysWith(200, "{\"other\":\"value\"}")
        val result = client().initClaim("uid-1")
        assertEquals(InitClaimResult.MalformedResponse, result)
    }
}

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
                    // unexpected; keep going
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
        500 -> "Internal Server Error"
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
