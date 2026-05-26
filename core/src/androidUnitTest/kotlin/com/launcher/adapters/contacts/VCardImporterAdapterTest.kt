package com.launcher.adapters.contacts

import com.launcher.api.contacts.ImportError
import com.launcher.api.contacts.RawVCard
import com.launcher.api.result.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Spec 009 Phase 5b real-adapter contract test for [VCardImporterAdapter]
 * (FR-028, plan §6). Validates against representative real-world vCard
 * samples (WhatsApp / Telegram / system Contacts export shapes).
 */
class VCardImporterAdapterTest {

    private val parser = VCardImporterAdapter()

    @Test
    fun whatsapp_sample_parses() = runTest {
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            N:;Маша;;;
            FN:Маша
            TEL;type=CELL;type=VOICE;waid=71234567890:+7 123 456 78 90
            END:VCARD
        """.trimIndent().replace("\n", "\r\n").toByteArray(Charsets.UTF_8)

        val r = parser.parse(payload).orFail()
        assertEquals("Маша", r.displayName)
        assertEquals(listOf("+7 123 456 78 90"), r.phoneNumbers)
    }

    @Test
    fun telegram_sample_parses() = runTest {
        val payload = """
            BEGIN:VCARD
            VERSION:4.0
            FN:Маша Иванова
            TEL:+71234567890
            END:VCARD
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val r = parser.parse(payload).orFail()
        assertEquals("Маша Иванова", r.displayName)
        assertEquals(listOf("+71234567890"), r.phoneNumbers)
    }

    @Test
    fun system_contacts_export_multiple_phones() = runTest {
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            N:Иванова;Маша;;;
            FN:Маша Иванова
            TEL;type=CELL:+71234567890
            TEL;type=HOME:+74951234567
            END:VCARD
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val r = parser.parse(payload).orFail()
        assertEquals("Маша Иванова", r.displayName)
        assertEquals(listOf("+71234567890", "+74951234567"), r.phoneNumbers)
    }

    @Test
    fun n_only_no_fn_uses_n() = runTest {
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            N:Иванов;Иван;;;
            TEL:+71234567890
            END:VCARD
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val r = parser.parse(payload).orFail()
        assertEquals("Иван Иванов", r.displayName)
    }

    @Test
    fun missing_tel_returns_MissingTel() = runTest {
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Маша
            END:VCARD
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val r = parser.parse(payload)
        assertTrue(r is Outcome.Failure)
        assertEquals(ImportError.MissingTel, r.error)
    }

    @Test
    fun missing_fn_and_n_returns_MissingFn() = runTest {
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            TEL:+71234567890
            END:VCARD
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val r = parser.parse(payload)
        assertTrue(r is Outcome.Failure)
        assertEquals(ImportError.MissingFn, r.error)
    }

    @Test
    fun oversize_returns_PayloadTooLarge() = runTest {
        val parser = VCardImporterAdapter(maxBytes = 100L)
        val payload = ("BEGIN:VCARD\nFN:" + "Х".repeat(1000) + "\nTEL:+71234567890\nEND:VCARD\n").toByteArray()
        val r = parser.parse(payload)
        assertTrue(r is Outcome.Failure)
        assertTrue(r.error is ImportError.PayloadTooLarge, "got ${r.error}")
    }

    @Test
    fun line_unfolding_works() = runTest {
        // RFC 6350 §3.2: continuation lines start with space/tab.
        val payload = ("BEGIN:VCARD\r\n" +
            "VERSION:3.0\r\n" +
            "FN:Маша\r\n" +
            " Иванова\r\n" +
            "TEL:+71234567890\r\n" +
            "END:VCARD\r\n").toByteArray(Charsets.UTF_8)

        val r = parser.parse(payload).orFail()
        assertEquals("МашаИванова", r.displayName)
    }

    @Test
    fun escaped_commas_and_semicolons_unescaped() = runTest {
        val payload = ("BEGIN:VCARD\nVERSION:3.0\nFN:Smith\\, John\nTEL:+71234567890\nEND:VCARD\n").toByteArray()
        val r = parser.parse(payload).orFail()
        assertEquals("Smith, John", r.displayName)
    }

    // ─── Spec 012 — PHOTO field extraction ────────────────────────────────

    @Test
    fun spec012_no_photo_field_returns_null_photoBytes() = runTest {
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Маша
            TEL:+71234567890
            END:VCARD
        """.trimIndent().replace("\n", "\r\n").toByteArray()
        val r = parser.parse(payload).orFail()
        assertTrue(r.photoBytes == null, "photoBytes должен быть null когда PHOTO line отсутствует")
    }

    @Test
    fun spec012_photo_encoding_b_base64_decoded() = runTest {
        // Tiny 1x1 PNG (8 bytes raw → base64 = 12 chars).
        val originalBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val base64 = java.util.Base64.getEncoder().encodeToString(originalBytes)
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Маша
            TEL:+71234567890
            PHOTO;ENCODING=b;TYPE=PNG:$base64
            END:VCARD
        """.trimIndent().replace("\n", "\r\n").toByteArray()
        val r = parser.parse(payload).orFail()
        val photo = r.photoBytes
        assertTrue(photo != null, "photoBytes должен быть non-null когда PHOTO present")
        assertTrue(photo.contentEquals(originalBytes), "decoded bytes должны matched original")
    }

    @Test
    fun spec012_photo_encoding_BASE64_uppercase_works() = runTest {
        val originalBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val base64 = java.util.Base64.getEncoder().encodeToString(originalBytes)
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Маша
            TEL:+71234567890
            PHOTO;ENCODING=BASE64:$base64
            END:VCARD
        """.trimIndent().replace("\n", "\r\n").toByteArray()
        val r = parser.parse(payload).orFail()
        assertTrue(r.photoBytes?.contentEquals(originalBytes) == true)
    }

    @Test
    fun spec012_photo_data_uri_inline_supported() = runTest {
        val originalBytes = byteArrayOf(0x10, 0x20, 0x30)
        val base64 = java.util.Base64.getEncoder().encodeToString(originalBytes)
        val payload = """
            BEGIN:VCARD
            VERSION:4.0
            FN:Маша
            TEL:+71234567890
            PHOTO:data:image/jpeg;base64,$base64
            END:VCARD
        """.trimIndent().replace("\n", "\r\n").toByteArray()
        val r = parser.parse(payload).orFail()
        assertTrue(r.photoBytes?.contentEquals(originalBytes) == true)
    }

    @Test
    fun spec012_photo_value_uri_form_returns_null() = runTest {
        // PHOTO;VALUE=URI form — out of scope для 012 (ACTION_PICK path вместо).
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Маша
            TEL:+71234567890
            PHOTO;VALUE=URI:http://example.com/photo.jpg
            END:VCARD
        """.trimIndent().replace("\n", "\r\n").toByteArray()
        val r = parser.parse(payload).orFail()
        assertTrue(r.photoBytes == null, "URI form НЕ supported в спеке 012 — возвращает null")
    }

    @Test
    fun spec012_photo_malformed_base64_returns_null() = runTest {
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Маша
            TEL:+71234567890
            PHOTO;ENCODING=b:!!!not-base64!!!
            END:VCARD
        """.trimIndent().replace("\n", "\r\n").toByteArray()
        val r = parser.parse(payload).orFail()
        assertTrue(r.photoBytes == null, "Malformed base64 → null photoBytes (no crash)")
    }

    private fun <T, E> Outcome<T, E>.orFail(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("expected Success, got Failure($error)")
    }
}
