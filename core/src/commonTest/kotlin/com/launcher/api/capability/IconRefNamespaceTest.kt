package com.launcher.api.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [IconRef] namespace convention validators per
 * [`contracts/icon-id-namespace.md`](specs/006-provider-capabilities-and-health/contracts/icon-id-namespace.md).
 */
class IconRefNamespaceTest {

    // -- isValid: positive cases -------------------------------------------

    @Test
    fun isValid_bundledKnownProvider() {
        assertTrue(IconRef.isValid("bundled:whatsapp"))
        assertTrue(IconRef.isValid("bundled:telegram"))
        assertTrue(IconRef.isValid("bundled:system_settings"))
    }

    @Test
    fun isValid_customWithUuid() {
        assertTrue(IconRef.isValid("custom:abc-123-def-456"))
    }

    @Test
    fun isValid_privateMedia() {
        assertTrue(IconRef.isValid("private:contact-photo-uuid-7890"))
    }

    @Test
    fun isValid_unknownNamespace_acceptedFormat() {
        // Forward-compat: future namespaces parse as valid format; resolution
        // returns NotFound at IconStorage level, not crash here.
        assertTrue(IconRef.isValid("future-ns:something"))
    }

    // -- isValid: negative cases -------------------------------------------

    @Test
    fun isValid_noSeparator_rejected() {
        assertFalse(IconRef.isValid("whatsapp"))
    }

    @Test
    fun isValid_emptyNamespace_rejected() {
        assertFalse(IconRef.isValid(":whatsapp"))
    }

    @Test
    fun isValid_emptyName_rejected() {
        assertFalse(IconRef.isValid("bundled:"))
    }

    @Test
    fun isValid_uppercaseNamespace_rejected() {
        assertFalse(IconRef.isValid("BUNDLED:whatsapp"))
    }

    @Test
    fun isValid_spaceInName_rejected() {
        assertFalse(IconRef.isValid("bundled:whats app"))
    }

    @Test
    fun isValid_nameTooLong_rejected() {
        // 129 chars in name — limit is 128.
        val tooLong = "bundled:" + "a".repeat(129)
        assertFalse(IconRef.isValid(tooLong))
    }

    @Test
    fun isValid_nameAtMaxLength_accepted() {
        // 128 chars exactly — boundary.
        val maxName = "bundled:" + "a".repeat(128)
        assertTrue(IconRef.isValid(maxName))
    }

    // -- bundled() builder --------------------------------------------------

    @Test
    fun bundled_buildsCorrectFormat() {
        assertEquals("bundled:whatsapp", IconRef.bundled("whatsapp"))
    }

    // -- namespaceOf / nameOf ----------------------------------------------

    @Test
    fun namespaceOf_validInput() {
        assertEquals("bundled", IconRef.namespaceOf("bundled:whatsapp"))
        assertEquals("custom", IconRef.namespaceOf("custom:abc"))
    }

    @Test
    fun namespaceOf_noColon_returnsNull() {
        assertNull(IconRef.namespaceOf("invalid"))
    }

    @Test
    fun namespaceOf_emptyString_returnsNull() {
        assertNull(IconRef.namespaceOf(""))
    }

    @Test
    fun nameOf_validInput() {
        assertEquals("whatsapp", IconRef.nameOf("bundled:whatsapp"))
    }

    @Test
    fun nameOf_noColon_returnsNull() {
        assertNull(IconRef.nameOf("invalid"))
    }

    @Test
    fun nameOf_emptyName_returnsNull() {
        assertNull(IconRef.nameOf("bundled:"))
    }
}
