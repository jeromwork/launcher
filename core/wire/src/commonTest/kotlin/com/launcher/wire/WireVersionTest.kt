package com.launcher.wire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/** Covers `docs/architecture/wire-format.md` §2 (identifier + comparison) and §4 (fail closed). */
class WireVersionTest {

    @Test
    fun parse_acceptsDottedForm() {
        assertEquals(WireVersion(2, 0), WireVersion.parse("2.0"))
        assertEquals(WireVersion(2, 1), WireVersion.parse("2.1"))
        assertEquals(WireVersion(3, 0, "beta"), WireVersion.parse("3.0-beta"))
    }

    @Test
    fun render_roundTripsThroughParse() {
        listOf("1.0", "2.13", "10.0", "3.0-beta", "3.0-preview.2").forEach { text ->
            assertEquals(text, WireVersion.parse(text).toString(), "roundtrip failed for '$text'")
        }
    }

    @Test
    fun compare_ordersMajorNumerically_notLexicographically() {
        // The whole reason the dotted form is compared by parts: "10.0" < "9.0" as text.
        assertTrue(WireVersion.parse("10.0") > WireVersion.parse("9.0"))
        assertTrue(WireVersion.parse("2.10") > WireVersion.parse("2.9"))
    }

    @Test
    fun compare_preReleaseSortsBelowTheSameVersionWithoutOne() {
        assertTrue(WireVersion.parse("3.0-beta") < WireVersion.parse("3.0"))
        assertTrue(WireVersion.parse("3.0-beta") > WireVersion.parse("2.9"))
    }

    @Test
    fun parse_rejectsBareInteger() {
        // Unconverted formats still carry `schemaVersion: 2`. A converted reader must fail
        // loudly rather than silently treat it as "2.0" (§4).
        assertFailsWith<UnknownWireVersionException> { WireVersion.parse("2") }
    }

    @Test
    fun parse_rejectsSemVerAndGarbage() {
        listOf("1.2.3", "v2.0", "", "  ", "2.", ".0", "2.0-", "abc").forEach { text ->
            assertFailsWith<UnknownWireVersionException>("expected '$text' to be rejected") {
                WireVersion.parse(text)
            }
        }
    }

    @Test
    fun parseOrNull_returnsNullInsteadOfThrowing() {
        assertNull(WireVersion.parseOrNull("2"))
        assertEquals(WireVersion(2, 0), WireVersion.parseOrNull("2.0"))
    }

    @Test
    fun serialize_isAJsonString() {
        assertEquals("\"2.1\"", Json.encodeToString(WireVersion.serializer(), WireVersion(2, 1)))
        assertEquals(
            WireVersion(3, 0, "beta"),
            Json.decodeFromString(WireVersion.serializer(), "\"3.0-beta\""),
        )
    }

    @Test
    fun deserialize_malformedStringFailsClosed() {
        assertFailsWith<UnknownWireVersionException> {
            Json.decodeFromString(WireVersion.serializer(), "\"2\"")
        }
    }

    @Test
    fun deserialize_preConversionIntegerReportsUnknownVersion_notCorrupt() {
        // The bare integer is what every format wrote before the conversion. The JSON decoder
        // rejects the token before our parse() runs, and its generic error reads as corrupt data —
        // §8 needs these distinguishable, so the serializer translates it.
        assertFailsWith<UnknownWireVersionException> {
            Json.decodeFromString(WireVersion.serializer(), "2")
        }
    }
}
