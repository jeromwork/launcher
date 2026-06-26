package cryptokit.crypto.api.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * KMP-portable Base64 codec — encodes [ByteArray] as a Base64 string in JSON, per
 * contracts/key-blob-v1.md (`wrappedKey`, `iv` are base64 strings, not number arrays).
 */
internal object ByteArrayBase64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("family.crypto.ByteArrayBase64", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.encode(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray =
        Base64.decode(decoder.decodeString())
}

/**
 * Minimal RFC 4648 §4 Base64 with padding. Pure Kotlin — usable in commonMain on any
 * KMP target without pulling JDK / iOS native APIs into the domain layer.
 */
internal object Base64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val LOOKUP: IntArray = IntArray(128) { -1 }.also {
        for (i in ALPHABET.indices) it[ALPHABET[i].code] = i
        it['='.code] = 0
    }

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xff
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else 0
            out.append(ALPHABET[b0 ushr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            if (i + 1 < bytes.size) {
                out.append(ALPHABET[((b1 and 0x0f) shl 2) or (b2 ushr 6)])
            } else {
                out.append('=')
            }
            if (i + 2 < bytes.size) {
                out.append(ALPHABET[b2 and 0x3f])
            } else {
                out.append('=')
            }
            i += 3
        }
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        val cleaned = text.filterNot { it.isWhitespace() }
        require(cleaned.length % 4 == 0) { "Invalid Base64 length: ${cleaned.length}" }
        if (cleaned.isEmpty()) return ByteArray(0)
        val padding = when {
            cleaned.endsWith("==") -> 2
            cleaned.endsWith("=") -> 1
            else -> 0
        }
        val outSize = (cleaned.length / 4) * 3 - padding
        val out = ByteArray(outSize)
        var oi = 0
        var i = 0
        while (i < cleaned.length) {
            val v0 = LOOKUP[cleaned[i].code]
            val v1 = LOOKUP[cleaned[i + 1].code]
            val v2 = LOOKUP[cleaned[i + 2].code]
            val v3 = LOOKUP[cleaned[i + 3].code]
            require(v0 >= 0 && v1 >= 0 && v2 >= 0 && v3 >= 0) {
                "Invalid Base64 character at offset $i"
            }
            val triple = (v0 shl 18) or (v1 shl 12) or (v2 shl 6) or v3
            if (oi < outSize) out[oi++] = ((triple ushr 16) and 0xff).toByte()
            if (oi < outSize) out[oi++] = ((triple ushr 8) and 0xff).toByte()
            if (oi < outSize) out[oi++] = (triple and 0xff).toByte()
            i += 4
        }
        return out
    }
}
