package family.push

import family.push.internal.PushPayloadWireFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * T044 — Forward-compat: receiver fail-soft на unknown future fields + on
 * `schemaVersion > MAX_SUPPORTED_SCHEMA_VERSION`. Per spec 019 FR-013,
 * Wire-format policy section in spec.md (asymmetric — client fail-soft).
 *
 * Worker fail-fast policy (returns 400 на future schemaVersion) tested separately
 * в workers/push/test/integration.test.ts (T082).
 */
class WireFormatForwardCompatTest {

    @Test
    fun parse_futureSchemaVersion_returnsNull_silentIgnore() {
        // Receiver sees schemaVersion = 2 (added в future build). Per fail-soft
        // policy → return null → LauncherFirebaseMessagingService logs + ignores.
        val flatMap = mapOf(
            "schemaVersion" to "2",
            "eventType" to "config-updated",
            "ownerUid" to "uid-1",
            "triggerId" to "trigger-001",
            "field_configName" to "main",
        )
        val parsed = PushPayloadWireFormat.parse(flatMap)
        assertNull(parsed, "Future schemaVersion must parse to null (fail-soft).")
    }

    @Test
    fun parse_unknownExtraField_isIgnored() {
        // Future build adds new reserved key (e.g., "deliveryAttempt"). Old
        // receiver ignores it. Additive change per FR-051.
        //
        // Note: PushPayloadWireFormat does NOT model "deliveryAttempt" — it
        // simply does not match any reserved key или field_ prefix. Such an
        // unknown key currently is silently dropped (NOT promoted к fields map).
        // This is the safe default — а new producer MUST also send same data
        // через documented field_ prefix or reserved key.
        val flatMap = mapOf(
            "schemaVersion" to "1",
            "eventType" to "config-updated",
            "ownerUid" to "uid-1",
            "triggerId" to "trigger-001",
            "field_configName" to "main",
            "deliveryAttempt" to "3",  // unknown top-level (not field_-prefixed)
        )
        val parsed = PushPayloadWireFormat.parse(flatMap)
        assertNotNull(parsed)
        assertEquals("config-updated", parsed.eventType)
        // deliveryAttempt не promoted в fields (no `field_` prefix).
        assertEquals(null, parsed.fields["deliveryAttempt"])
    }

    @Test
    fun parse_unknownEventType_returnsPayload_callerHandlesIgnore() {
        // PushPayloadWireFormat.parse does NOT validate eventType existence —
        // that's responsibility of LauncherFirebaseMessagingService via
        // EventType.fromWireOrNull. Parser just decodes shape.
        val flatMap = mapOf(
            "schemaVersion" to "1",
            "eventType" to "future-feature-not-known-yet",
            "ownerUid" to "uid-1",
            "triggerId" to "trigger-001",
        )
        val parsed = PushPayloadWireFormat.parse(flatMap)
        assertNotNull(parsed)
        assertEquals("future-feature-not-known-yet", parsed.eventType)
        // Caller (receiver) does EventType.fromWireOrNull → null → silent ignore.
    }

    @Test
    fun parse_missingSchemaVersion_returnsNull() {
        val flatMap = mapOf(
            "eventType" to "config-updated",
            "ownerUid" to "uid-1",
            "triggerId" to "trigger-001",
        )
        assertNull(PushPayloadWireFormat.parse(flatMap))
    }

    @Test
    fun parse_missingTriggerId_returnsNull() {
        val flatMap = mapOf(
            "schemaVersion" to "1",
            "eventType" to "config-updated",
            "ownerUid" to "uid-1",
        )
        assertNull(PushPayloadWireFormat.parse(flatMap))
    }

    @Test
    fun parse_missingEventType_returnsNull() {
        val flatMap = mapOf(
            "schemaVersion" to "1",
            "ownerUid" to "uid-1",
            "triggerId" to "trigger-001",
        )
        assertNull(PushPayloadWireFormat.parse(flatMap))
    }
}
