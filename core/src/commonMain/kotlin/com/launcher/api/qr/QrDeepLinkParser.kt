package com.launcher.api.qr

import com.launcher.api.pairing.PairingToken
import family.wire.WireVersion

/**
 * Parser and builder for QR pairing deep links of shape
 * `launcher://pair?token=<TOKEN>&v=<MIN_READER>` (contracts/qr-deeplink.md).
 *
 * **Read-once transport — a single version field.** The link is scanned exactly once and
 * discarded; the receiver never writes it back. Per `docs/architecture/wire-format.md` §3, a
 * format with no read-modify-write cycle carries only `minReaderVersion`: the other two fields
 * describe states that cannot occur here — `minWriterVersion` gates a write-back that never
 * happens, and `schemaVersion` is a diagnostic no reader acts on. Grounding: otpauth://, WiFi
 * QR, EMV, age and CTAP all version read-once payloads with one field or none; the reader/writer
 * split (SQLite, Matroska) exists only where a format is both read *and* written by differing
 * versions. Keeping the field to one also keeps the QR from getting denser — it is scanned from
 * a phone screen by hand, so every character is pixels.
 *
 * `v` is that single field, dotted (`"1.0"`). The reader refuses a link requiring a reader newer
 * than this build (§4, fail closed) so the admin UI can prompt "update the app" instead of
 * accepting a token it cannot reason about. An additive future change keeps `v` unchanged (old
 * readers still cope, unknown params are tolerated); only a breaking change raises it.
 *
 * Lives in `commonMain` and is platform-free — no `android.net.Uri`. The URI grammar is small
 * enough to hand-parse, which keeps the test surface platform-agnostic.
 */
object QrDeepLinkParser {

    const val SCHEME: String = "launcher"
    const val HOST: String = "pair"

    /**
     * The minimum reader version the current link format requires — the single version field of
     * this read-once format (wire-format.md §3). It is both the value [buildPairingDeepLink]
     * stamps into `v` and the ceiling [parsePairingDeepLink] gates against, because this build
     * reads exactly the format it writes. A breaking change raises this; an additive one does not.
     */
    val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

    sealed interface QrParseResult {
        data class Success(val token: PairingToken) : QrParseResult
        data object InvalidScheme : QrParseResult
        data object UnsupportedVersion : QrParseResult
        data object MalformedToken : QrParseResult
    }

    /**
     * Builds the deep link a fresh QR encodes. The single source of the URI grammar — the display
     * screen calls this rather than assembling the string itself, so the format lives in one place.
     */
    fun buildPairingDeepLink(token: PairingToken): String =
        "$SCHEME://$HOST?token=${token.raw}&v=$MIN_READER_VERSION"

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

        // `v` is the minimum reader this link requires. Refuse anything newer than we implement
        // (§4, fail closed); an absent or unparseable version cannot clear the gate either. No
        // legacy default — pre-MVP, so no old links exist in the field to stay compatible with.
        val requiredReader = params["v"]?.let { WireVersion.parseOrNull(it) }
            ?: return QrParseResult.UnsupportedVersion
        if (requiredReader > MIN_READER_VERSION) return QrParseResult.UnsupportedVersion

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
