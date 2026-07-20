package family.push

import com.launcher.wire.WireVersion

import family.push.internal.PushPayloadWireFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * T043 — Backward-compat parse: receiver MUST read legacy v1 payloads
 * containing `linkId` field (spec 007/008 era). Per FR-051, contracts/push-payload-v1.md
 * §`linkId: String?` (DEPRECATED).
 *
 * Legacy spec 007 PushPayload shape mapping to F-5c shape:
 *   • `linkId` → preserved as nullable field в new PushPayload.
 *   • `type` (CommandIssued, ConfigChanged, Revoke) — see Phase 4 migration
 *     (T200-T207). PushType wire values bridged через PushHandlerRegistry.
 *
 * Per Phase 4 migration plan, LauncherPushReceiver (T206) bridges legacy
 * PushType wire values как PushHandler impls keyed на их wireValue strings.
 */
class WireFormatBackwardCompatTest {

    @Test
    fun parse_legacyFlatMapWithLinkId_preservesLinkId() {
        // Legacy 008 push: only linkId, no eventType/triggerId in wire payload?
        // Actually legacy spec 007 PushPayloadWireFormat used `type` + `linkId`.
        // After Phase 4 migration LauncherPushReceiver bridges это к new shape
        // by mapping `type` → `eventType` + generating triggerId if absent.
        //
        // Here we test the **new** wire format с linkId field set
        // (FR-051 — additive change preservation).
        val flatMap = mapOf(
            "schemaVersion" to "1.0", "minReaderVersion" to "1.0", "minWriterVersion" to "1.0",
            "eventType" to "config-updated",
            "ownerUid" to "uid-legacy",
            "triggerId" to "trigger-legacy-001",
            "linkId" to "legacy-pair-abc123",
            "field_configName" to "main",
        )
        val parsed = PushPayloadWireFormat.parse(flatMap)
        assertNotNull(parsed)
        assertEquals("legacy-pair-abc123", parsed.linkId)
        assertEquals("config-updated", parsed.eventType)
        assertEquals("main", parsed.fields["configName"])
    }

    @Test
    fun parse_payloadWithoutLinkId_linkIdIsNull() {
        // F-5c canonical shape — no linkId on new events.
        val flatMap = mapOf(
            "schemaVersion" to "1.0", "minReaderVersion" to "1.0", "minWriterVersion" to "1.0",
            "eventType" to "config-updated",
            "ownerUid" to "uid-1",
            "triggerId" to "trigger-001",
            "field_configName" to "main",
        )
        val parsed = PushPayloadWireFormat.parse(flatMap)
        assertNotNull(parsed)
        assertEquals(null, parsed.linkId)
    }
}
