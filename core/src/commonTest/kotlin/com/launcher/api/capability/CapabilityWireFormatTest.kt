package com.launcher.api.capability

import com.launcher.api.action.ProviderId
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wire-format tests for [Capability] per
 * [`contracts/capability-wire-format.md`](specs/006-provider-capabilities-and-health/contracts/capability-wire-format.md)
 * v1.0.0. Covers:
 *  - Roundtrip every field combination (FR-006, SC-003).
 *  - Optional-field defaults via @Serializable (FR-042).
 *  - Forward-compat: schemaVersion > SUPPORTED parses without crash (FR-043, SC-015).
 *  - Unknown extra fields ignored (ignoreUnknownKeys policy).
 */
class CapabilityWireFormatTest {

    private val json = WireFormatJson.json

    private fun roundtrip(c: Capability): Capability =
        json.decodeFromString(Capability.serializer(), json.encodeToString(Capability.serializer(), c))

    // -- Roundtrip: every field combination --------------------------------

    @Test
    fun roundtrip_minimal_unavailableProvider() {
        val original = Capability(
            providerId = ProviderId.WHATSAPP,
            displayName = "WhatsApp",
            iconId = "bundled:whatsapp",
            available = false,
        )
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_availableWithVersionCode() {
        val original = Capability(
            providerId = ProviderId.WHATSAPP,
            displayName = "WhatsApp",
            iconId = "bundled:whatsapp",
            available = true,
            versionCode = 241800L,
        )
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_withSha256() {
        val original = Capability(
            providerId = ProviderId.fromWire("smart_assistant"),
            displayName = "Smart Assistant",
            iconId = "custom:smart-assistant-uuid",
            iconSha256 = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            available = true,
            versionCode = 1L,
        )
        assertEquals(original, roundtrip(original))
    }

    @Test
    fun roundtrip_listOfCapabilities() {
        // The actual on-disk shape is List<Capability>.
        val original = listOf(
            Capability(providerId = ProviderId.WHATSAPP, displayName = "WhatsApp",
                       iconId = "bundled:whatsapp", available = true, versionCode = 241800L),
            Capability(providerId = ProviderId.TELEGRAM, displayName = "Telegram",
                       iconId = "bundled:telegram", available = false),
        )
        val serializer = ListSerializer(Capability.serializer())
        val decoded = json.decodeFromString(serializer, json.encodeToString(serializer, original))
        assertEquals(original, decoded)
    }

    // -- Defaults policy (FR-042) ------------------------------------------

    @Test
    fun missingOptionalFields_useDefaults() {
        // No iconSha256, no versionCode — must parse with nulls.
        val wire = """{"providerId":"phone","displayName":"Phone","iconId":"bundled:phone","available":true}"""
        val parsed = json.decodeFromString(Capability.serializer(), wire)
        assertNull(parsed.iconSha256)
        assertNull(parsed.versionCode)
        assertEquals(1, parsed.schemaVersion) // companion default
    }

    // -- Forward-compat: future schemaVersion (FR-043, SC-015) -------------

    @Test
    fun futureSchemaVersion_parsesBestEffort() {
        // schemaVersion 999 + extra unknown field "futureFeature": parsing must
        // not crash; known fields populate; schemaVersion is preserved as-is so
        // consumer can decide to downgrade behaviour.
        val wire = """{
            "schemaVersion": 999,
            "providerId": "whatsapp",
            "displayName": "WhatsApp",
            "iconId": "bundled:whatsapp",
            "available": true,
            "versionCode": 241800,
            "futureFeature": {"experimentalFlag": true},
            "anotherUnknown": [1, 2, 3]
        }"""
        val parsed = json.decodeFromString(Capability.serializer(), wire)
        assertEquals(999, parsed.schemaVersion)
        assertEquals(ProviderId.WHATSAPP, parsed.providerId)
        assertEquals("WhatsApp", parsed.displayName)
        assertEquals("bundled:whatsapp", parsed.iconId)
        assertTrue(parsed.available)
        assertEquals(241800L, parsed.versionCode)
    }

    // -- Forward-compat: unknown iconId namespace --------------------------

    @Test
    fun unknownIconNamespace_parsesWithoutException() {
        // FR-009: unknown namespace must parse — IconStorage handles it at
        // resolve time. The data class init must NOT validate iconId.
        val wire = """{
            "providerId":"whatsapp","displayName":"WhatsApp",
            "iconId":"future-namespace:abc","available":true
        }"""
        val parsed = json.decodeFromString(Capability.serializer(), wire)
        assertEquals("future-namespace:abc", parsed.iconId)
    }

    // -- encodeDefaults=false: null fields stay off the wire ---------------

    @Test
    fun nullOptionalFields_omittedFromWire() {
        val c = Capability(
            providerId = ProviderId.PHONE,
            displayName = "Phone",
            iconId = "bundled:phone",
            available = true,
        )
        val wire = json.encodeToString(Capability.serializer(), c)
        // No "iconSha256":null, no "versionCode":null — encodeDefaults=false.
        assertTrue(!wire.contains("iconSha256"), "wire should not contain null iconSha256: $wire")
        assertTrue(!wire.contains("versionCode"), "wire should not contain null versionCode: $wire")
    }
}
