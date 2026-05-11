package com.launcher.api.qr

import com.launcher.api.pairing.PairingToken

/**
 * Parser for QR deep links of shape
 * `launcher://pair?token=<TOKEN>&v=<SCHEMA>`
 * (contracts/qr-deeplink.md v1).
 *
 * Lives in `commonMain` and is platform-free — no `android.net.Uri`. The
 * URI grammar is small enough to hand-parse, which keeps the test surface
 * platform-agnostic.
 *
 * Forward-compat:
 *  - Missing `v=` defaults to `1` for legacy QRs (contract §Edge cases).
 *  - Unknown extra query params are tolerated.
 *  - `v >= 2` from the wire produces [QrParseResult.UnsupportedVersion] so
 *    the admin UI can prompt "update the app" instead of accepting a token
 *    it cannot reason about.
 */
object QrDeepLinkParser {

    const val SCHEME: String = "launcher"
    const val HOST: String = "pair"
    const val SUPPORTED_VERSION: Int = 1

    sealed interface QrParseResult {
        data class Success(val token: PairingToken) : QrParseResult
        data object InvalidScheme : QrParseResult
        data object UnsupportedVersion : QrParseResult
        data object MalformedToken : QrParseResult
    }

    fun parsePairingDeepLink(uri: String): QrParseResult {
        val schemeSeparator = uri.indexOf("://")
        if (schemeSeparator <= 0) return QrParseResult.InvalidScheme

        val scheme = uri.substring(0, schemeSeparator)
        if (scheme != SCHEME) return QrParseResult.InvalidScheme

        val afterScheme = uri.substring(schemeSeparator + 3)
        val queryStart = afterScheme.indexOf('?')

        val host: String
        val query: String
        if (queryStart < 0) {
            host = afterScheme
            query = ""
        } else {
            host = afterScheme.substring(0, queryStart)
            query = afterScheme.substring(queryStart + 1)
        }

        if (host != HOST) return QrParseResult.InvalidScheme

        val params = parseQuery(query)

        // Missing `v` is treated as v=1 (legacy compat per contract §Edge cases).
        // TODO(v2): when a v=2 ships, add a `when` branch above; do NOT remove
        // the legacy default — old QRs still need to parse for at least one
        // major release (CLAUDE.md §5).
        val version = params["v"]?.toIntOrNull() ?: SUPPORTED_VERSION
        if (version != SUPPORTED_VERSION) return QrParseResult.UnsupportedVersion

        val rawToken = params["token"] ?: return QrParseResult.MalformedToken
        val token = try {
            PairingToken(rawToken)
        } catch (_: IllegalArgumentException) {
            return QrParseResult.MalformedToken
        }
        return QrParseResult.Success(token)
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            if (eq < 0) {
                out[percentDecode(pair)] = ""
            } else {
                val key = percentDecode(pair.substring(0, eq))
                val value = percentDecode(pair.substring(eq + 1))
                out[key] = value
            }
        }
        return out
    }

    private fun percentDecode(s: String): String {
        if (!s.contains('%') && !s.contains('+')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> { sb.append(' '); i++ }
                c == '%' && i + 2 < s.length -> {
                    val hex = s.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(radix = 16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 3
                    } else {
                        sb.append(c); i++
                    }
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }
}
