package family.crypto.util

/** Lowercase hex encode + decode for KAT vector wiring. Test-only, pure Kotlin. */
internal fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

internal fun String.hexToByteArray(): ByteArray {
    val cleaned = filterNot { it.isWhitespace() || it == ':' }
    require(cleaned.length % 2 == 0) { "Hex string length must be even, got ${cleaned.length}" }
    val out = ByteArray(cleaned.length / 2)
    for (i in out.indices) {
        out[i] = ((hexDigit(cleaned[2 * i]) shl 4) or hexDigit(cleaned[2 * i + 1])).toByte()
    }
    return out
}

private fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c.code - '0'.code
    in 'a'..'f' -> c.code - 'a'.code + 10
    in 'A'..'F' -> c.code - 'A'.code + 10
    else -> throw IllegalArgumentException("Invalid hex digit '$c'")
}
