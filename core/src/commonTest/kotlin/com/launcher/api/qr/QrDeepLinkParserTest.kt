package com.launcher.api.qr

import com.launcher.api.qr.QrDeepLinkParser.QrParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parser tests per
 * [`contracts/qr-deeplink.md`](specs/007-pairing-and-firebase-channel/contracts/qr-deeplink.md)
 * v1.0.0.
 */
class QrDeepLinkParserTest {

    @Test
    fun parses_valid_link() {
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B&v=1")
        assertTrue(result is QrParseResult.Success, "got $result")
        assertEquals("A3KX9B", result.token.raw)
    }

    @Test
    fun rejects_invalid_scheme() {
        val result = QrDeepLinkParser.parsePairingDeepLink("https://launcher.example/pair?token=A3KX9B&v=1")
        assertEquals(QrParseResult.InvalidScheme, result)
    }

    @Test
    fun rejects_invalid_host() {
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://other?token=A3KX9B&v=1")
        assertEquals(QrParseResult.InvalidScheme, result)
    }

    @Test
    fun rejects_invalid_token_chars() {
        // 0 is forbidden in the alphabet — token A3KX0B contains 0.
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX0B&v=1")
        assertEquals(QrParseResult.MalformedToken, result)
    }

    @Test
    fun rejects_unsupported_version() {
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B&v=2")
        assertEquals(QrParseResult.UnsupportedVersion, result)
    }

    @Test
    fun tolerates_extra_params() {
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B&v=1&foo=bar")
        assertTrue(result is QrParseResult.Success)
        assertEquals("A3KX9B", result.token.raw)
    }

    @Test
    fun missing_v_defaults_to_supported() {
        // Legacy compat per contract §Edge cases — missing v means v=1.
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B")
        assertTrue(result is QrParseResult.Success)
        assertEquals("A3KX9B", result.token.raw)
    }

    @Test
    fun missing_token_is_malformed() {
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?v=1")
        assertEquals(QrParseResult.MalformedToken, result)
    }
}
