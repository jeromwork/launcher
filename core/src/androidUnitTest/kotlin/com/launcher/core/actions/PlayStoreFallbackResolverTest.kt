package com.launcher.core.actions

import com.launcher.api.action.ActionPayload
import com.launcher.api.action.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreFallbackResolverTest {

    private val resolver = PlayStoreFallbackResolver()

    @Test
    fun primary_marketUriForPackage() {
        val action = resolver.resolve("com.example.app")
        val payload = action.payload as ActionPayload.OpenApp

        assertEquals(ProviderId.APP, action.providerId)
        assertEquals(PlayStoreFallbackResolver.PLAY_STORE_PACKAGE, payload.packageHint)
        assertEquals("market://details?id=com.example.app", payload.storeUrlHint)
    }

    @Test
    fun secondary_webPlayStoreUrl() {
        val action = resolver.resolve("com.example.app")
        val fallback = action.fallback!!
        val webPayload = fallback.payload as ActionPayload.Url

        assertEquals(ProviderId.BROWSER, fallback.providerId)
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.example.app",
            webPayload.url,
        )
    }

    @Test
    fun blankPackageName_throws() {
        try {
            resolver.resolve("")
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().isNotBlank())
        }
    }
}
