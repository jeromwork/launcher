package com.launcher.api.link

import family.wire.WireVersion

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.wireformat.WireFormatJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wire-format tests for `/links/{linkId}/state/current` bootstrap per
 * [`contracts/state-bootstrap.md`](specs/007-pairing-and-firebase-channel/contracts/state-bootstrap.md)
 * v1.0.0.
 */
class LinkBootstrapWireFormatTest {

    private val json = WireFormatJson.json

    @Test
    fun roundtrip_with_fcm_token() {
        val original = LinkBootstrap(
            appliedAt = 1746974400000L,
            presetId = "simple-launcher",
            fcmToken = "fAk3FcmT0k3n_eXampLe",
        )
        val out = LinkBootstrapWireFormat.serialize(original)
        val parsed = LinkBootstrapWireFormat.deserialize(out).orFail()
        assertEquals(original, parsed)
    }

    @Test
    fun roundtrip_with_null_fcm_token_gms_absent_case() {
        val original = LinkBootstrap(
            appliedAt = 1746974400000L,
            presetId = "simple-launcher",
            fcmToken = null,
        )
        val out = LinkBootstrapWireFormat.serialize(original)
        // null fcmToken must NOT appear in the wire body — encodeDefaults=false equivalent.
        assertTrue(
            !out.containsKey("fcmToken"),
            "null fcmToken should be omitted from wire: $out",
        )
        val parsed = LinkBootstrapWireFormat.deserialize(out).orFail()
        assertNull(parsed.fcmToken)
        assertEquals("simple-launcher", parsed.presetId)
    }

    @Test
    fun backwardCompat_unknown_extra_fields_tolerated() {
        // Spec 008 will add flows/slots — current v1 reader must parse known
        // fields and ignore the rest.
        val wire = """
            {
              "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "appliedAt": 1746974400000,
              "presetId": "simple-launcher",
              "fcmToken": "tok",
              "flows": [{"id": "x"}],
              "slots": {}
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire)
        val parsed = LinkBootstrapWireFormat.deserialize(element).orFail()
        assertEquals("tok", parsed.fcmToken)
    }

    @Test
    fun unknown_future_version_rejected() {
        val wire = """
            {
              "schemaVersion": "999.0", "minReaderVersion": "999.0", "minWriterVersion": "999.0",
              "appliedAt": 1746974400000,
              "presetId": "simple-launcher"
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire)
        val result = LinkBootstrapWireFormat.deserialize(element)
        assertTrue(result is Outcome.Failure)
    }

    @Test
    fun parseSchemaVersionOnly_returnsTheDottedVersion() {
        val element = json.parseToJsonElement("""{"schemaVersion": "1.0"}""")
        assertEquals(WireVersion(1, 0), LinkBootstrapWireFormat.parseSchemaVersionOnly(element))
    }

    @Test
    fun parseSchemaVersionOnly_returns_null_for_malformed() {
        val element = json.parseToJsonElement("""{"other": 1}""")
        assertNull(LinkBootstrapWireFormat.parseSchemaVersionOnly(element))
    }

    private fun Outcome<LinkBootstrap, BackendError>.orFail(): LinkBootstrap =
        when (this) {
            is Outcome.Success -> value
            is Outcome.Failure -> fail("Expected success, got Failure($error)")
        }
}
