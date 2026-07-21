package com.launcher.api.qr

import com.launcher.api.pairing.PairingToken
import com.launcher.api.qr.QrDeepLinkParser.QrParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parser tests per
 * [`contracts/qr-deeplink.md`](specs/007-pairing-and-firebase-channel/contracts/qr-deeplink.md).
 *
 * The link is a read-once transport carrying a single dotted version field `v`, read as
 * `minReaderVersion` (docs/architecture/wire-format.md §3). The reader gate compares rather than
 * equates: a link is accepted when it requires a reader no newer than this build.
 */
class QrDeepLinkParserTest {

    @Test
    fun parses_valid_link() {
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B&v=1.0")
        assertTrue(result is QrParseResult.Success, "got $result")
        assertEquals("A3KX9B", result.token.raw)
    }

    @Test
    fun builder_and_parser_roundtrip() {
        // Encode → decode returns the original token, and the emitted link carries the dotted
        // version this build stamps — proof the one grammar source stays self-consistent.
        val token = PairingToken("A3KX9B")
        val link = QrDeepLinkParser.buildPairingDeepLink(token)
        assertEquals("launcher://pair?token=A3KX9B&v=1.0", link)
        val result = QrDeepLinkParser.parsePairingDeepLink(link)
        assertTrue(result is QrParseResult.Success, "got $result")
        assertEquals("A3KX9B", result.token.raw)
    }

    @Test
    fun rejects_invalid_scheme() {
        val result = QrDeepLinkParser.parsePairingDeepLink("https://launcher.example/pair?token=A3KX9B&v=1.0")
        assertEquals(QrParseResult.InvalidScheme, result)
    }

    @Test
    fun rejects_invalid_host() {
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://other?token=A3KX9B&v=1.0")
        assertEquals(QrParseResult.InvalidScheme, result)
    }

    @Test
    fun rejects_invalid_token_chars() {
        // 0 is forbidden in the alphabet — token A3KX0B contains 0.
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX0B&v=1.0")
        assertEquals(QrParseResult.MalformedToken, result)
    }

    @Test
    fun rejects_link_requiring_a_newer_reader() {
        // v=2.0 demands a reader newer than this build (1.0) → refuse, fail closed (§4).
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B&v=2.0")
        assertEquals(QrParseResult.UnsupportedVersion, result)
    }

    @Test
    fun accepts_same_version_with_unknown_future_params() {
        // The gate compares, it does not equate: an additive future change keeps v at 1.0 and adds
        // params an old reader has never seen. Those are tolerated, so the link still parses — the
        // behaviour the old equality gate wrongly rejected (AC #3).
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B&v=1.0&flow=fast")
        assertTrue(result is QrParseResult.Success, "got $result")
        assertEquals("A3KX9B", result.token.raw)
    }

    @Test
    fun rejects_malformed_version() {
        // A version we cannot parse (bare integer, garbage) fails closed rather than being guessed
        // at (§4) — the admin UI prompts "update the app" via UnsupportedVersion.
        assertEquals(
            QrParseResult.UnsupportedVersion,
            QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B&v=1"),
        )
        assertEquals(
            QrParseResult.UnsupportedVersion,
            QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B&v=abc"),
        )
    }

    @Test
    fun rejects_missing_version() {
        // No legacy default — pre-MVP, no old links exist. A link without `v` cannot clear the gate.
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?token=A3KX9B")
        assertEquals(QrParseResult.UnsupportedVersion, result)
    }

    @Test
    fun missing_token_is_malformed() {
        val result = QrDeepLinkParser.parsePairingDeepLink("launcher://pair?v=1.0")
        assertEquals(QrParseResult.MalformedToken, result)
    }
}
