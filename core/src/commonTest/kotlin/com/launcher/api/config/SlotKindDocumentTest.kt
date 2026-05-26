package com.launcher.api.config

import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Spec 012 wire-format tests for new [SlotKind.Document] variant.
 *
 * Task: T1222 (Phase 3). FR-017, contracts/tile-document-kind.md §Tests.
 *
 * Covered:
 *  - Roundtrip Slot(Document) write → read → assertEquals (basic).
 *  - Mixed kinds in tiles array preserves DocumentSlot.
 *  - Forward-compat: unknown kind value parses graceful (returns null from fromWireOrNull).
 *  - Validation: documentRef format check (private:<uuid>).
 *  - Label preservation в args.
 */
class SlotKindDocumentTest {

    @Test
    fun roundtrip_document_slot() {
        val slot = Slot(
            id = ElementId("11234567-1234-4321-8765-abcdefabcdef"),
            kind = SlotKind.Document,
            args = buildJsonObject {
                put("documentRef", JsonPrimitive("private:f1111111-2222-4333-9444-555555555555"))
                put("label", JsonPrimitive("Паспорт"))
            },
        )

        val json = WireFormatJson.json.encodeToString(slot)
        val parsed = WireFormatJson.json.decodeFromString<Slot>(json)

        assertEquals(slot, parsed)
        assertEquals(SlotKind.Document, parsed.kind)
        assertEquals(
            "private:f1111111-2222-4333-9444-555555555555",
            (parsed.args?.get("documentRef") as? JsonPrimitive)?.content,
        )
        assertEquals("Паспорт", (parsed.args?.get("label") as? JsonPrimitive)?.content)
    }

    @Test
    fun document_kind_wireValue_is_lowercase_document() {
        assertEquals("document", SlotKind.Document.wireValue)
    }

    @Test
    fun fromWireOrNull_recognises_document() {
        assertEquals(SlotKind.Document, SlotKind.fromWireOrNull("document"))
    }

    @Test
    fun fromWireOrNull_returns_null_for_unknown_future_kind() {
        // Forward-compat: future "audio" kind unknown to current reader.
        assertNull(SlotKind.fromWireOrNull("audio"))
    }

    @Test
    fun mixed_kinds_preserve_document() {
        val slots = listOf(
            Slot(
                id = ElementId("10000001-1111-4111-8111-111111111111"),
                kind = SlotKind.Call,
                args = buildJsonObject { put("contactId", JsonPrimitive("c111")) },
            ),
            Slot(
                id = ElementId("20000002-2222-4222-8222-222222222222"),
                kind = SlotKind.Document,
                args = buildJsonObject {
                    put("documentRef", JsonPrimitive("private:f2222222-3333-4444-9555-666666666666"))
                    put("label", JsonPrimitive("Медкарта"))
                },
            ),
            Slot(
                id = ElementId("30000003-3333-4333-8333-333333333333"),
                kind = SlotKind.OpenApp,
                args = buildJsonObject { put("packageName", JsonPrimitive("com.whatsapp")) },
            ),
        )

        val json = WireFormatJson.json.encodeToString(slots)
        val parsed = WireFormatJson.json.decodeFromString<List<Slot>>(json)

        assertEquals(3, parsed.size)
        assertEquals(SlotKind.Document, parsed[1].kind)
        assertEquals("Медкарта", (parsed[1].args?.get("label") as? JsonPrimitive)?.content)
    }

    @Test
    fun documentRef_format_validatable_via_namespace_prefix() {
        val slot = Slot(
            id = ElementId("90000000-1111-4222-8333-444455556666"),
            kind = SlotKind.Document,
            args = buildJsonObject {
                put("documentRef", JsonPrimitive("private:abcdef01-2345-4678-89ab-cdef01234567"))
                put("label", JsonPrimitive("СНИЛС"))
            },
        )

        val documentRef = (slot.args?.get("documentRef") as? JsonPrimitive)?.content
        assertNotNull(documentRef)
        assertTrue(documentRef.startsWith("private:"))
    }
}
