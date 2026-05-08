package com.launcher.api.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Asserts that on-disk fixture files in
 * `core/src/commonTest/resources/fixtures/action-wire-format/` parse into the
 * exact same [Action] shapes that [ActionWireFormatTest] verifies inline. This
 * catches drift between the two: if a fixture file is edited but the inline
 * test isn't (or vice-versa), this test fails on its own.
 *
 * Per spec 005 T521 / Clarification C4 — fixtures must exist as files for
 * human review even though tests use inline literals for ergonomics.
 *
 * Runs in `androidUnitTest` (not `commonTest`) because reading files from disk
 * requires `java.io.File`, which is JVM-only. Source-set choice is incidental;
 * the contract under test is pure-Kotlin.
 */
class ActionWireFormatFixtureTest {

    private val fixturesDir: File =
        File("src/commonTest/resources/fixtures/action-wire-format").also {
            check(it.isDirectory) {
                "fixtures dir not found at ${it.absolutePath}; cwd=${File("").absolutePath}"
            }
        }

    private fun read(name: String): String = File(fixturesDir, name).readText()

    // -- v1 fixtures: every payload variant ------------------------------

    @Test
    fun fixture_openApp() {
        assertEquals(
            Action(
                providerId = ProviderId.APP,
                payload = ActionPayload.OpenApp(
                    packageHint = "com.example.app",
                    storeUrlHint = "market://details?id=com.example.app",
                ),
            ),
            ActionWireFormat.decode(read("open-app-v1.json")),
        )
    }

    @Test
    fun fixture_whatsappMessage() {
        assertEquals(
            Action(
                providerId = ProviderId.WHATSAPP,
                payload = ActionPayload.WhatsAppMessage("alice"),
            ),
            ActionWireFormat.decode(read("whatsapp-message-v1.json")),
        )
    }

    @Test
    fun fixture_whatsappCall_voice() {
        assertEquals(
            Action(
                providerId = ProviderId.WHATSAPP,
                payload = ActionPayload.WhatsAppCall("alice", WhatsAppCallKind.VOICE),
            ),
            ActionWireFormat.decode(read("whatsapp-call-voice-v1.json")),
        )
    }

    @Test
    fun fixture_whatsappCall_video() {
        assertEquals(
            Action(
                providerId = ProviderId.WHATSAPP,
                payload = ActionPayload.WhatsAppCall("bob", WhatsAppCallKind.VIDEO),
            ),
            ActionWireFormat.decode(read("whatsapp-call-video-v1.json")),
        )
    }

    @Test
    fun fixture_phone() {
        assertEquals(
            Action(
                providerId = ProviderId.PHONE,
                payload = ActionPayload.Phone("+74951234567"),
            ),
            ActionWireFormat.decode(read("phone-v1.json")),
        )
    }

    @Test
    fun fixture_sms() {
        assertEquals(
            Action(
                providerId = ProviderId.SMS,
                payload = ActionPayload.Sms("+79991234567", "Optional pre-filled body"),
            ),
            ActionWireFormat.decode(read("sms-v1.json")),
        )
    }

    @Test
    fun fixture_url() {
        assertEquals(
            Action(
                providerId = ProviderId.BROWSER,
                payload = ActionPayload.Url("https://example.com/path?q=1"),
            ),
            ActionWireFormat.decode(read("url-v1.json")),
        )
    }

    @Test
    fun fixture_youtubeHome() {
        assertEquals(
            Action(
                providerId = ProviderId.YOUTUBE,
                payload = ActionPayload.YouTube(YouTubeTarget.Home),
            ),
            ActionWireFormat.decode(read("youtube-home-v1.json")),
        )
    }

    @Test
    fun fixture_youtubeVideo() {
        assertEquals(
            Action(
                providerId = ProviderId.YOUTUBE,
                payload = ActionPayload.YouTube(YouTubeTarget.Video("dQw4w9WgXcQ")),
            ),
            ActionWireFormat.decode(read("youtube-video-v1.json")),
        )
    }

    @Test
    fun fixture_youtubeChannel() {
        assertEquals(
            Action(
                providerId = ProviderId.YOUTUBE,
                payload = ActionPayload.YouTube(YouTubeTarget.Channel("@example")),
            ),
            ActionWireFormat.decode(read("youtube-channel-v1.json")),
        )
    }

    @Test
    fun fixture_openSettings() {
        assertEquals(
            Action(
                providerId = ProviderId.SYSTEM_SETTINGS,
                payload = ActionPayload.OpenSettings(SettingsTarget.General),
            ),
            ActionWireFormat.decode(read("open-settings-v1.json")),
        )
    }

    @Test
    fun fixture_customEmpty() {
        assertEquals(
            Action(
                providerId = ProviderId.fromWire("smart_assistant"),
                payload = ActionPayload.Custom("ask"),
            ),
            ActionWireFormat.decode(read("custom-empty-v1.json")),
        )
    }

    @Test
    fun fixture_customPopulated() {
        assertEquals(
            Action(
                providerId = ProviderId.fromWire("smart_assistant"),
                payload = ActionPayload.Custom(
                    key = "ask",
                    params = mapOf("prompt" to "schedule meeting", "lang" to "ru"),
                ),
            ),
            ActionWireFormat.decode(read("custom-populated-v1.json")),
        )
    }

    @Test
    fun fixture_fallbackChain() {
        val parsed = ActionWireFormat.decode(read("fallback-chain-v1.json"))
        assertEquals(ProviderId.WHATSAPP, parsed.providerId)
        assertEquals(ProviderId.APP, parsed.fallback?.providerId)
        assertEquals(ProviderId.BROWSER, parsed.fallback?.fallback?.providerId)
        assertNull(parsed.fallback?.fallback?.fallback)
    }

    // -- legacy spec 003 fixtures ---------------------------------------

    @Test
    fun fixture_legacyWhatsappCallVoice() {
        assertEquals(
            Action(
                providerId = ProviderId.WHATSAPP,
                payload = ActionPayload.WhatsAppCall("alice", WhatsAppCallKind.VOICE),
            ),
            ActionWireFormat.migrateLegacyAction(read("legacy-spec003-whatsapp-call-voice.json")),
        )
    }

    @Test
    fun fixture_legacyWhatsappCallVideo() {
        assertEquals(
            Action(
                providerId = ProviderId.WHATSAPP,
                payload = ActionPayload.WhatsAppCall("bob", WhatsAppCallKind.VIDEO),
            ),
            ActionWireFormat.migrateLegacyAction(read("legacy-spec003-whatsapp-call-video.json")),
        )
    }

    @Test
    fun fixture_legacyWhatsappMessage() {
        assertEquals(
            Action(
                providerId = ProviderId.WHATSAPP,
                payload = ActionPayload.WhatsAppMessage("carol"),
            ),
            ActionWireFormat.migrateLegacyAction(read("legacy-spec003-whatsapp-message.json")),
        )
    }

    @Test
    fun fixture_legacyOpenApp() {
        assertEquals(
            Action(
                providerId = ProviderId.APP,
                payload = ActionPayload.OpenApp(packageHint = "com.example"),
            ),
            ActionWireFormat.migrateLegacyAction(read("legacy-spec003-open-app.json")),
        )
    }

    @Test
    fun fixture_legacyPlaceholder_returnsNull() {
        assertNull(ActionWireFormat.migrateLegacyAction(read("legacy-spec003-placeholder.json")))
    }

    @Test
    fun fixture_directoryListedInReadme() {
        // Sanity check: every *.json fixture is mentioned in README.md.
        val readme = File(fixturesDir, "README.md").readText()
        val unlisted = fixturesDir.listFiles { f -> f.extension == "json" }
            ?.map { it.name }
            ?.filter { it !in readme }
            .orEmpty()
        assertEquals("fixtures missing from README.md: $unlisted", emptyList<String>(), unlisted)
    }
}
