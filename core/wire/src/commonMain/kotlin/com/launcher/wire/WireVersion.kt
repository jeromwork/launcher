package com.launcher.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The version identifier carried by every wire format — `docs/architecture/wire-format.md` §2.
 *
 * Dotted `MAJOR.MINOR` plus an optional pre-release token: `"2.0"`, `"2.1"`, `"3.0-beta"`.
 * Deliberately **not** SemVer (§13) — no patch component, and its precedence rules describe
 * released artifacts rather than data shapes, so they are not applied here.
 *
 * Ordering (§2): MAJOR numerically, then MINOR numerically; a pre-release token sorts *below*
 * the same version without one, because a pre-release is a waiver of the stability guarantee
 * and must never outrank the stable release it precedes.
 *
 * Serialized as a JSON string. A malformed value raises [UnknownWireVersionException] rather
 * than degrading to a best guess (§4 — fail closed).
 */
@Serializable(with = WireVersionSerializer::class)
data class WireVersion(
    val major: Int,
    val minor: Int,
    val preRelease: String? = null,
) : Comparable<WireVersion> {

    init {
        require(major >= 0) { "major must not be negative: $major" }
        require(minor >= 0) { "minor must not be negative: $minor" }
        require(preRelease == null || preRelease.isNotBlank()) {
            "pre-release token must not be blank — omit it instead"
        }
    }

    override fun compareTo(other: WireVersion): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        return when {
            preRelease == other.preRelease -> 0
            // Absent token outranks any token at the same MAJOR.MINOR.
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String =
        if (preRelease == null) "$major.$minor" else "$major.$minor-$preRelease"

    companion object {
        private val PATTERN = Regex("""^(\d+)\.(\d+)(?:-([0-9A-Za-z.\-]+))?$""")

        /**
         * Parses the dotted form. Throws [UnknownWireVersionException] on anything else —
         * including the bare integers written by formats that predate this discipline, which
         * is intentional: an unconverted document must fail loudly at a converted reader
         * rather than be silently read as `"N.0"`.
         */
        fun parse(text: String): WireVersion {
            val match = PATTERN.matchEntire(text.trim())
                ?: throw UnknownWireVersionException(
                    "Malformed wire version '$text' — expected MAJOR.MINOR with an optional " +
                        "pre-release token, e.g. \"2.0\" or \"3.0-beta\"."
                )
            val (major, minor, preRelease) = match.destructured
            return WireVersion(
                major = major.toIntOrNull()
                    ?: throw UnknownWireVersionException("Version component out of range: '$text'"),
                minor = minor.toIntOrNull()
                    ?: throw UnknownWireVersionException("Version component out of range: '$text'"),
                preRelease = preRelease.ifEmpty { null },
            )
        }

        /** Returns `null` instead of throwing — for callers that classify rather than read. */
        fun parseOrNull(text: String): WireVersion? =
            runCatching { parse(text) }.getOrNull()
    }
}

internal object WireVersionSerializer : KSerializer<WireVersion> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.launcher.wire.WireVersion", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: WireVersion) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): WireVersion {
        // A non-string token here is a pre-conversion document carrying the old integer form.
        // The format-level decoder would report that as a generic decoding failure, which reads
        // as "corrupt" — but the document is intact, we are simply too old to know its shape.
        // §8 requires those two to stay distinguishable, so translate to the unknown-version error.
        val text = try {
            decoder.decodeString()
        } catch (e: SerializationException) {
            throw UnknownWireVersionException(
                "Wire version is not a string — this is the pre-conversion integer form. " +
                    "Expected a dotted version such as \"1.0\".",
                e,
            )
        }
        return WireVersion.parse(text)
    }
}
