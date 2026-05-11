package com.launcher.core.capability

import com.launcher.api.capability.IconResolution
import com.launcher.core.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Tests for [BundledIconStorage] per [`contracts/icon-id-namespace.md`](specs/006-provider-capabilities-and-health/contracts/icon-id-namespace.md)
 * §"Test contract".
 *
 * RobolectricTestRunner gives us R.drawable.* resolution; the storage class
 * itself doesn't touch any network / DataStore, so test setup is trivial.
 */
@RunWith(RobolectricTestRunner::class)
class BundledIconStorageTest {

    private val storage = BundledIconStorage(logger = null)

    // -- All 8 known providers resolve to Drawable -------------------------

    @Test
    fun bundled_app_resolvesToDrawable() {
        assertEquals(IconResolution.Drawable(R.drawable.provider_app), storage.resolve("bundled:app"))
    }

    @Test
    fun bundled_whatsapp_resolvesToDrawable() {
        assertEquals(IconResolution.Drawable(R.drawable.provider_whatsapp), storage.resolve("bundled:whatsapp"))
    }

    @Test
    fun bundled_telegram_resolvesToDrawable() {
        assertEquals(IconResolution.Drawable(R.drawable.provider_telegram), storage.resolve("bundled:telegram"))
    }

    @Test
    fun bundled_phone_resolvesToDrawable() {
        assertEquals(IconResolution.Drawable(R.drawable.provider_phone), storage.resolve("bundled:phone"))
    }

    @Test
    fun bundled_sms_resolvesToDrawable() {
        assertEquals(IconResolution.Drawable(R.drawable.provider_sms), storage.resolve("bundled:sms"))
    }

    @Test
    fun bundled_browser_resolvesToDrawable() {
        assertEquals(IconResolution.Drawable(R.drawable.provider_browser), storage.resolve("bundled:browser"))
    }

    @Test
    fun bundled_youtube_resolvesToDrawable() {
        assertEquals(IconResolution.Drawable(R.drawable.provider_youtube), storage.resolve("bundled:youtube"))
    }

    @Test
    fun bundled_systemSettings_resolvesToDrawable() {
        assertEquals(IconResolution.Drawable(R.drawable.provider_system_settings), storage.resolve("bundled:system_settings"))
    }

    // -- Edge cases per contract -------------------------------------------

    @Test
    fun bundled_unknownName_returnsPlaceholder() {
        // Known namespace, missing resource → Placeholder + missing_resource log.
        assertEquals(IconResolution.Placeholder, storage.resolve("bundled:nonexistent"))
    }

    @Test
    fun customNamespace_returnsPlaceholder() {
        // Reserved namespace (claimed by spec 007), no resolver in spec 006.
        assertEquals(IconResolution.Placeholder, storage.resolve("custom:abc-123"))
    }

    @Test
    fun privateNamespace_returnsPlaceholder() {
        // Reserved namespace (claimed by spec 011), no resolver in spec 006.
        assertEquals(IconResolution.Placeholder, storage.resolve("private:contact-photo-uuid"))
    }

    @Test
    fun unknownNamespace_returnsNotFound() {
        assertEquals(IconResolution.NotFound, storage.resolve("future-namespace:abc"))
    }

    @Test
    fun invalidFormat_noColon_returnsNotFound() {
        assertEquals(IconResolution.NotFound, storage.resolve("whatsapp"))
    }

    @Test
    fun invalidFormat_emptyNamespace_returnsNotFound() {
        assertEquals(IconResolution.NotFound, storage.resolve(":whatsapp"))
    }

    @Test
    fun invalidFormat_emptyName_returnsNotFound() {
        assertEquals(IconResolution.NotFound, storage.resolve("bundled:"))
    }

    @Test
    fun invalidFormat_uppercaseNamespace_returnsNotFound() {
        assertEquals(IconResolution.NotFound, storage.resolve("BUNDLED:whatsapp"))
    }
}
