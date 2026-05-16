package com.launcher.adapters.contacts

import com.launcher.api.contacts.ImportError
import com.launcher.api.contacts.RawVCard
import com.launcher.api.contacts.VCardImporter
import com.launcher.api.result.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hand-written vCard 3.0/4.0 parser — FN + TEL only (spec 009 FR-028,
 * plan §5). No `ezvcard` library — would leak vendor types into domain
 * (CLAUDE.md rule 1).
 *
 * Whitelist policy: only the `FN`, `N`, and `TEL` properties are
 * extracted. Everything else (PHOTO, ADR, EMAIL, custom X-* fields) is
 * ignored. Line unfolding per RFC 6350 §3.2 (a single-space or single-tab
 * continuation joins the previous line).
 *
 * NFR-002: p95 parse < 100 ms on Pixel 4a class for 10 KB payload.
 * Implementation is single-pass + cap on input length — no regex
 * back-tracking.
 *
 * Caveats handled:
 *  - Quoted-printable encoding (`ENCODING=QUOTED-PRINTABLE`) — best-effort
 *    decoded; on parse failure the raw value is used.
 *  - Multiple `TEL` fields — all collected in order.
 *  - `FN` preferred over `N`; `N` parsed as `last;first;...` joined by space.
 */
class VCardImporterAdapter(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : VCardImporter {

    override suspend fun parse(payload: ByteArray): Outcome<RawVCard, ImportError> =
        withContext(Dispatchers.Default) { parseInternal(payload) }

    private fun parseInternal(payload: ByteArray): Outcome<RawVCard, ImportError> {
        if (payload.size > maxBytes) {
            return Outcome.Failure(
                ImportError.PayloadTooLarge(sizeBytes = payload.size.toLong(), maxBytes = maxBytes),
            )
        }

        val text = try {
            payload.toString(Charsets.UTF_8).also {
                // Quick sanity: if decoding produced replacement chars from
                // non-UTF-8 input, treat as NonUtf8. We err on the side of
                // permissiveness — most vCards in the wild are UTF-8.
                if (it.contains('�') && payload.any { b -> b.toInt() and 0xFF >= 0x80 }) {
                    return Outcome.Failure(ImportError.NonUtf8)
                }
            }
        } catch (_: Throwable) {
            return Outcome.Failure(ImportError.NonUtf8)
        }

        val unfolded = unfoldLines(text)

        var fn: String? = null
        var n: String? = null
        val telephones = mutableListOf<String>()

        for (line in unfolded) {
            val sep = line.indexOf(':')
            if (sep <= 0) continue
            val headerRaw = line.substring(0, sep)
            val value = line.substring(sep + 1)
            val headerParts = headerRaw.split(';')
            val name = headerParts[0].trim().uppercase()
            val params = headerParts.drop(1).map { it.trim() }

            when (name) {
                "FN" -> if (fn == null) fn = decodeValue(value, params)
                "N" -> if (n == null) n = decodeNValue(value, params)
                "TEL" -> telephones.add(decodeValue(value, params))
            }
        }

        val displayName = fn?.takeIf { it.isNotBlank() }
            ?: n?.takeIf { it.isNotBlank() }
            ?: return Outcome.Failure(ImportError.MissingFn)

        if (telephones.isEmpty()) {
            return Outcome.Failure(ImportError.MissingTel)
        }

        return Outcome.Success(
            RawVCard(
                displayName = displayName.trim(),
                phoneNumbers = telephones.map { it.trim() }.filter { it.isNotEmpty() },
            ).let { v ->
                if (v.phoneNumbers.isEmpty()) return Outcome.Failure(ImportError.MissingTel)
                v
            },
        )
    }

    private fun unfoldLines(text: String): List<String> {
        val raw = text.lineSequence().toList()
        val out = mutableListOf<String>()
        for (line in raw) {
            if (line.isEmpty()) continue
            val first = line[0]
            if (first == ' ' || first == '\t') {
                if (out.isNotEmpty()) {
                    out[out.lastIndex] = out.last() + line.substring(1)
                }
            } else {
                out.add(line)
            }
        }
        return out
    }

    private fun decodeValue(raw: String, params: List<String>): String {
        val isQuotedPrintable = params.any { it.equals("ENCODING=QUOTED-PRINTABLE", ignoreCase = true) }
        val decoded = if (isQuotedPrintable) decodeQuotedPrintable(raw) else raw
        // Unescape vCard escapes per RFC 6350: \n \, \; \\.
        return decoded
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private fun decodeNValue(raw: String, params: List<String>): String {
        val decoded = decodeValue(raw, params)
        // RFC 6350 §6.2.2: N = Family;Given;Additional;Prefix;Suffix.
        val parts = decoded.split(';')
        val family = parts.getOrNull(0)?.trim().orEmpty()
        val given = parts.getOrNull(1)?.trim().orEmpty()
        return listOf(given, family).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun decodeQuotedPrintable(input: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '=' && i + 2 < input.length) {
                val hex = input.substring(i + 1, i + 3)
                val v = hex.toIntOrNull(16)
                if (v != null) {
                    bytes.add(v.toByte())
                    i += 3
                    continue
                }
            }
            bytes.add(c.code.toByte())
            i++
        }
        return try {
            bytes.toByteArray().toString(Charsets.UTF_8)
        } catch (_: Throwable) {
            input
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 10L * 1024L  // 10 KB cap per plan §5 + research R-008.
    }
}
