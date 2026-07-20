package family.push.api

import family.wire.WireVersion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests для public façade [PushPayload.parseFromFcmData]. Wraps internal
 * PushPayloadWireFormat — these tests verify the public seam, not the wire
 * format (latter tested в WireFormatRoundtripTest).
 */
class PushPayloadParseTest {

    @Test
    fun parseFromFcmData_validNewShape_returnsPayload() {
        val data = mapOf(
            "schemaVersion" to "1.0", "minReaderVersion" to "1.0", "minWriterVersion" to "1.0",
            "eventType" to "config-updated",
            "ownerUid" to "owner-1",
            "triggerId" to "trigger-001",
            "field_configName" to "main",
        )
        val parsed = PushPayload.parseFromFcmData(data)
        assertNotNull(parsed)
        assertEquals("config-updated", parsed.eventType)
        assertEquals("owner-1", parsed.ownerUid)
        assertEquals("trigger-001", parsed.triggerId)
        assertEquals("main", parsed.fields["configName"])
    }

    @Test
    fun parseFromFcmData_emptyMap_returnsNull() {
        assertNull(PushPayload.parseFromFcmData(emptyMap()))
    }

    @Test
    fun parseFromFcmData_missingTriggerId_returnsNull() {
        val data = mapOf(
            "schemaVersion" to "1.0", "minReaderVersion" to "1.0", "minWriterVersion" to "1.0",
            "eventType" to "config-updated",
            "ownerUid" to "owner-1",
        )
        assertNull(PushPayload.parseFromFcmData(data))
    }

    @Test
    fun parseFromFcmData_futureSchemaVersion_returnsNull_failSoft() {
        val data = mapOf(
            "schemaVersion" to "2.0", "minReaderVersion" to "2.0", "minWriterVersion" to "2.0",
            "eventType" to "config-updated",
            "ownerUid" to "owner-1",
            "triggerId" to "t-1",
        )
        assertNull(PushPayload.parseFromFcmData(data))
    }
}
