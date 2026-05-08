package com.launcher.api.action

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wire-format tests for [Action] per spec 005 §8 fitness function 2:
 * roundtrip every payload variant, plus forward-compat (unknown id) and
 * backward-compat (legacy spec 003 shape).
 *
 * Inline-string fixtures here are also mirrored as files in
 * `core/src/commonTest/resources/fixtures/action-wire-format/` for human
 * readability and external diff review (Clarification C4).
 */
class ActionWireFormatTest {

    // ---------------------------------------------------------------------
    // Roundtrip: every ActionPayload variant (Phase 2 / T520)
    // ---------------------------------------------------------------------

    @Test
    fun roundtrip_openApp() {
        val original = Action(
            providerId = ProviderId.APP,
            payload = ActionPayload.OpenApp(
                packageHint = "com.example.app",
                storeUrlHint = "market://details?id=com.example.app",
            ),
        )
        assertEquals(original, ActionWireFormat.decode(ActionWireFormat.encode(original)))
    }

    @Test
    fun roundtrip_whatsappMessage() {
        val original = Action(
            providerId = ProviderId.WHATSAPP,
            payload = ActionPayload.WhatsAppMessage(contactRef = "alice"),
        )
        assertEquals(original, ActionWireFormat.decode(ActionWireFormat.encode(original)))
    }

    @Test
    fun roundtrip_whatsappCall_voiceAndVideo() {
        for (kind in WhatsAppCallKind.entries) {
            val original = Action(
                providerId = ProviderId.WHATSAPP,
                payload = ActionPayload.WhatsAppCall(contactRef = "bob", kind = kind),
            )
            assertEquals(original, ActionWireFormat.decode(ActionWireFormat.encode(original)),
                "roundtrip failed for kind=$kind")
        }
    }

    @Test
    fun roundtrip_phone() {
        val original = Action(
            providerId = ProviderId.PHONE,
            payload = ActionPayload.Phone(number = "+74951234567"),
        )
        assertEquals(original, ActionWireFormat.decode(ActionWireFormat.encode(original)))
    }

    @Test
    fun roundtrip_sms_withAndWithoutBody() {
        val withBody = Action(
            providerId = ProviderId.SMS,
            payload = ActionPayload.Sms(number = "+79991234567", body = "ping"),
        )
        val noBody = Action(
            providerId = ProviderId.SMS,
            payload = ActionPayload.Sms(number = "+79991234567"),
        )
        assertEquals(withBody, ActionWireFormat.decode(ActionWireFormat.encode(withBody)))
        assertEquals(noBody, ActionWireFormat.decode(ActionWireFormat.encode(noBody)))
    }

    @Test
    fun roundtrip_url() {
        val original = Action(
            providerId = ProviderId.BROWSER,
            payload = ActionPayload.Url(url = "https://example.com/path?q=1"),
        )
        assertEquals(original, ActionWireFormat.decode(ActionWireFormat.encode(original)))
    }

    @Test
    fun roundtrip_youtube_allTargets() {
        val targets: List<YouTubeTarget> = listOf(
            YouTubeTarget.Home,
            YouTubeTarget.Video(videoId = "dQw4w9WgXcQ"),
            YouTubeTarget.Channel(channelHandle = "@example"),
        )
        for (target in targets) {
            val original = Action(
                providerId = ProviderId.YOUTUBE,
                payload = ActionPayload.YouTube(target),
            )
            assertEquals(original, ActionWireFormat.decode(ActionWireFormat.encode(original)),
                "roundtrip failed for target=$target")
        }
    }

    @Test
    fun roundtrip_openSettings() {
        val original = Action(
            providerId = ProviderId.SYSTEM_SETTINGS,
            payload = ActionPayload.OpenSettings(target = SettingsTarget.General),
        )
        assertEquals(original, ActionWireFormat.decode(ActionWireFormat.encode(original)))
    }

    @Test
    fun roundtrip_custom_emptyAndPopulatedParams() {
        val empty = Action(
            providerId = ProviderId.fromWire("smart_assistant"),
            payload = ActionPayload.Custom(key = "ask", params = emptyMap()),
        )
        val populated = Action(
            providerId = ProviderId.fromWire("smart_assistant"),
            payload = ActionPayload.Custom(
                key = "ask",
                params = mapOf("prompt" to "schedule meeting", "lang" to "ru"),
            ),
        )
        assertEquals(empty, ActionWireFormat.decode(ActionWireFormat.encode(empty)))
        assertEquals(populated, ActionWireFormat.decode(ActionWireFormat.encode(populated)))
    }

    @Test
    fun roundtrip_fallbackChain_depth2() {
        val original = Action(
            providerId = ProviderId.WHATSAPP,
            payload = ActionPayload.WhatsAppMessage("alice"),
            fallback = Action(
                providerId = ProviderId.APP,
                payload = ActionPayload.OpenApp("com.whatsapp", "market://details?id=com.whatsapp"),
                fallback = Action(
                    providerId = ProviderId.BROWSER,
                    payload = ActionPayload.Url("https://wa.me/"),
                ),
            ),
        )
        val roundtripped = ActionWireFormat.decode(ActionWireFormat.encode(original))
        assertEquals(original, roundtripped)
        assertNotNull(roundtripped.fallback?.fallback)
        assertNull(roundtripped.fallback?.fallback?.fallback)
    }

    @Test
    fun roundtrip_nullFallback_isOmittedFromWire() {
        val original = Action(
            providerId = ProviderId.PHONE,
            payload = ActionPayload.Phone(number = "+1"),
            // fallback omitted (null)
        )
        val wire = ActionWireFormat.encode(original)
        // encodeDefaults = false -> null fallback should not appear on the wire
        assertTrue("\"fallback\"" !in wire, "expected fallback to be omitted, got wire: $wire")
        assertEquals(original, ActionWireFormat.decode(wire))
    }

    // ---------------------------------------------------------------------
    // Forward compatibility: unknown providerId / unknown payload kind (T522)
    // ---------------------------------------------------------------------

    @Test
    fun unknownProviderId_parses_OK_andDispatchSurfacesUnknown() {
        // Per Clarification C1: parse must not fail on unknown providerId.
        // Dispatch (in production) translates this into ProviderUnavailable(UnknownInThisVersion).
        val wire = """
            {"schemaVersion":1,"providerId":"smart_assistant","payload":{"kind":"custom","key":"ask"}}
        """.trimIndent()
        val parsed = ActionWireFormat.decode(wire)
        assertEquals("smart_assistant", parsed.providerId.value)
    }

    @Test
    fun unknownPayloadKind_failsAtParse() {
        // kotlinx.serialization throws on unknown discriminator value. Production
        // dispatcher translates this to DispatchResult.Failure("unknown payload kind").
        val wire = """
            {"schemaVersion":1,"providerId":"smart_assistant","payload":{"kind":"hologram_call"}}
        """.trimIndent()
        assertFailsWith<SerializationException> {
            ActionWireFormat.decode(wire)
        }
    }

    @Test
    fun futureSchemaVersion_parses_butIsRejectedAtDispatchTime() {
        // schemaVersion > SUPPORTED_SCHEMA_VERSION must NOT throw at parse time —
        // the caller (dispatcher) decides what to do (per spec §7.1 step 1).
        val wire = """
            {"schemaVersion":99,"providerId":"app","payload":{"kind":"open_app","packageHint":"x"}}
        """.trimIndent()
        val parsed = ActionWireFormat.decode(wire)
        assertEquals(99, parsed.schemaVersion)
        assertTrue(parsed.schemaVersion > Action.SUPPORTED_SCHEMA_VERSION)
    }

    @Test
    fun unknownPayloadField_isIgnored_perIgnoreUnknownKeys() {
        // Forward-compat: producers may add fields; older readers ignore them.
        val wire = """
            {"schemaVersion":1,"providerId":"phone","payload":{"kind":"phone","number":"+1","newField":"value"}}
        """.trimIndent()
        val parsed = ActionWireFormat.decode(wire)
        assertEquals("+1", (parsed.payload as ActionPayload.Phone).number)
    }

    // ---------------------------------------------------------------------
    // ProviderId.fromWire validation
    // ---------------------------------------------------------------------

    @Test
    fun providerId_fromWire_rejectsInvalid() {
        for (bad in listOf("", "X", "Whatsapp", "with space", "no!", "1leading", "_under", "z")) {
            assertFailsWith<IllegalArgumentException>("expected reject for: '$bad'") {
                ProviderId.fromWire(bad)
            }
        }
    }

    @Test
    fun providerId_fromWire_acceptsValid() {
        for (good in listOf("ab", "whatsapp", "telegram", "smart_assistant", "x-y-z", "abc123")) {
            ProviderId.fromWire(good) // should not throw
        }
    }

    // ---------------------------------------------------------------------
    // Backward compatibility: spec 003 legacy shapes via migrateLegacyAction (T523)
    // ---------------------------------------------------------------------

    @Test
    fun legacyMigration_whatsappCall_voice() {
        val legacy = """{"type":"whatsapp_call","contactRef":"alice","actionType":"voice"}"""
        val migrated = ActionWireFormat.migrateLegacyAction(legacy)
        val expected = Action(
            providerId = ProviderId.WHATSAPP,
            payload = ActionPayload.WhatsAppCall("alice", WhatsAppCallKind.VOICE),
        )
        assertEquals(expected, migrated)
    }

    @Test
    fun legacyMigration_whatsappCall_video() {
        val legacy = """{"type":"whatsapp_call","contactRef":"bob","actionType":"video"}"""
        val migrated = ActionWireFormat.migrateLegacyAction(legacy)
        val expected = Action(
            providerId = ProviderId.WHATSAPP,
            payload = ActionPayload.WhatsAppCall("bob", WhatsAppCallKind.VIDEO),
        )
        assertEquals(expected, migrated)
    }

    @Test
    fun legacyMigration_whatsappMessage() {
        val legacy = """{"type":"whatsapp_message","contactRef":"carol"}"""
        val migrated = ActionWireFormat.migrateLegacyAction(legacy)
        val expected = Action(
            providerId = ProviderId.WHATSAPP,
            payload = ActionPayload.WhatsAppMessage("carol"),
        )
        assertEquals(expected, migrated)
    }

    @Test
    fun legacyMigration_openApp() {
        val legacy = """{"type":"open_app","packageName":"com.example"}"""
        val migrated = ActionWireFormat.migrateLegacyAction(legacy)
        val expected = Action(
            providerId = ProviderId.APP,
            payload = ActionPayload.OpenApp(packageHint = "com.example"),
        )
        assertEquals(expected, migrated)
    }

    @Test
    fun legacyMigration_placeholder_returnsNull() {
        val legacy = """{"type":"placeholder"}"""
        assertNull(ActionWireFormat.migrateLegacyAction(legacy))
    }

    @Test
    fun legacyMigration_alreadyNewFormat_passesThrough() {
        val original = Action(
            providerId = ProviderId.PHONE,
            payload = ActionPayload.Phone("+1"),
        )
        val wire = ActionWireFormat.encode(original)
        val migrated = ActionWireFormat.migrateLegacyAction(wire)
        assertEquals(original, migrated)
    }

    @Test
    fun legacyMigration_unknownLegacyType_fails() {
        val legacy = """{"type":"hologram_call","contactRef":"x"}"""
        assertFailsWith<IllegalArgumentException> {
            ActionWireFormat.migrateLegacyAction(legacy)
        }
    }

    @Test
    fun legacyMigration_missingType_fails() {
        val legacy = """{"contactRef":"x"}"""
        assertFailsWith<IllegalArgumentException> {
            ActionWireFormat.migrateLegacyAction(legacy)
        }
    }

    @Test
    fun legacyMigration_unknownActionType_fails() {
        val legacy = """{"type":"whatsapp_call","contactRef":"x","actionType":"holo"}"""
        assertFailsWith<IllegalArgumentException> {
            ActionWireFormat.migrateLegacyAction(legacy)
        }
    }
}
