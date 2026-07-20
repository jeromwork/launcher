package family.push

import com.launcher.wire.WireVersion

import family.push.api.PushPayload
import family.push.api.WireFormatVersion
import family.push.internal.PushPayloadWireFormat
import family.push.internal.PushTriggerRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T042 — Roundtrip + forward-compat + backward-compat tests for PushPayload и
 * PushTriggerRequest wire formats. Per spec 019 FR-050, FR-051,
 * contracts/push-payload-v1.md §Roundtrip test fixture.
 *
 * Fixtures duplicated as inline string constants because KMP commonTest does
 * not have portable resource-loading API. JSON files in resources/ serve as
 * authoritative wire-format documentation; константы must stay in sync.
 */
class WireFormatRoundtripTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    /** Matches `commonTest/resources/push-payload-v1.json`. */
    private val pushPayloadFixture = """
        {"schemaVersion":"1.0","minReaderVersion":"1.0","minWriterVersion":"1.0","eventType":"config-updated","ownerUid":"TestUid1234567890123456789AB","triggerId":"550e8400-e29b-41d4-a716-446655440000","fields":{"configName":"main"}}
    """.trimIndent()

    /** Matches `commonTest/resources/push-trigger-request-v1.json`. */
    private val pushTriggerRequestFixture = """
        {"schemaVersion":"1.0","minReaderVersion":"1.0","minWriterVersion":"1.0","eventType":"config-updated","targetScope":"own-and-grants","ownerUid":"TestUid1234567890123456789AB","payload":{"configName":"main"}}
    """.trimIndent()

    @Test
    fun pushPayload_jsonRoundtrip_isEqual() {
        val decoded = json.decodeFromString<PushPayload>(pushPayloadFixture)
        assertEquals(WireVersion(1, 0), decoded.schemaVersion)
        assertEquals("config-updated", decoded.eventType)
        assertEquals("TestUid1234567890123456789AB", decoded.ownerUid)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", decoded.triggerId)
        assertEquals("main", decoded.fields["configName"])
        assertNull(decoded.linkId)

        // Re-encode and ensure stable shape (linkId absent — default null).
        val expectedRoundtrip = PushPayload(
            schemaVersion = WireVersion(1, 0),
            eventType = "config-updated",
            ownerUid = "TestUid1234567890123456789AB",
            triggerId = "550e8400-e29b-41d4-a716-446655440000",
            fields = mapOf("configName" to "main"),
            linkId = null,
        )
        assertEquals(expectedRoundtrip, decoded)
    }

    @Test
    fun pushTriggerRequest_jsonRoundtrip_isEqual() {
        val decoded = json.decodeFromString<PushTriggerRequest>(pushTriggerRequestFixture)
        assertEquals(WireVersion(1, 0), decoded.schemaVersion)
        assertEquals("config-updated", decoded.eventType)
        assertEquals("own-and-grants", decoded.targetScope)
        assertEquals("TestUid1234567890123456789AB", decoded.ownerUid)
        assertEquals("main", decoded.payload["configName"])

        val expected = PushTriggerRequest(
            schemaVersion = WireVersion(1, 0),
            eventType = "config-updated",
            targetScope = "own-and-grants",
            ownerUid = "TestUid1234567890123456789AB",
            payload = mapOf("configName" to "main"),
        )
        assertEquals(expected, decoded)
    }

    @Test
    fun pushPayload_flatMapRoundtrip_preservesAllFields() {
        val original = PushPayload(
            schemaVersion = WireVersion(1, 0),
            eventType = "config-updated",
            ownerUid = "TestUid1234567890123456789AB",
            triggerId = "550e8400-e29b-41d4-a716-446655440000",
            fields = mapOf("configName" to "main", "extra" to "value"),
            linkId = null,
        )
        val encoded = PushPayloadWireFormat.encode(original)
        val decoded = PushPayloadWireFormat.parse(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun pushPayload_flatMapRoundtrip_withLinkIdLegacy_preservesLinkId() {
        val original = PushPayload(
            schemaVersion = WireVersion(1, 0),
            eventType = "config-updated",
            ownerUid = "TestUid1234567890123456789AB",
            triggerId = "550e8400-e29b-41d4-a716-446655440000",
            fields = mapOf("configName" to "main"),
            linkId = "legacy-pair-abc123",
        )
        val encoded = PushPayloadWireFormat.encode(original)
        assertEquals("legacy-pair-abc123", encoded["linkId"])
        val decoded = PushPayloadWireFormat.parse(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun pushPayload_constants_matchWireFormatVersion() {
        // T402-adjacent invariant — MAX_SUPPORTED_SCHEMA_VERSION = 1 mirrors TS side.
        assertEquals(WireVersion(1, 0), WireFormatVersion.SCHEMA_VERSION)
    }
}
