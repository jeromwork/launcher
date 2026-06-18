package com.launcher.adapters.auth

import android.util.Base64
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests для T750 extractSubClaim. Robolectric нужен для
 * `android.util.Base64` (real Android Base64 — host JVM не имеет URL_SAFE).
 *
 * Полные end-to-end сценарии (Credential Manager + Firebase exchange) —
 * см. instrumentation tests `GoogleSignInAdapterInstrumentationTest`
 * (T756, Session D, требует эмулятора).
 *
 * Per spec 017 plan §"Test Strategy" #3, T750, FR-016a.
 */
@RunWith(RobolectricTestRunner::class)
class GoogleIdTokenParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun extractSubClaim_returnsSubFromValidGoogleIdToken() {
        val payloadJson = """{"sub":"1234567890","email":"test@example.com"}"""
        val payloadB64 = Base64.encodeToString(
            payloadJson.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val fakeIdToken = "header.$payloadB64.signature"

        val sub = GoogleIdTokenParser.extractSubClaim(fakeIdToken, json)
        assertEquals("1234567890", sub)
    }

    @Test
    fun extractSubClaim_throwsOnMalformedToken() {
        assertThrows(IllegalArgumentException::class.java) {
            GoogleIdTokenParser.extractSubClaim("not-a-jwt", json)
        }
    }

    @Test
    fun extractSubClaim_throwsOnPayloadWithoutSub() {
        val payloadJson = """{"email":"test@example.com"}"""
        val payloadB64 = Base64.encodeToString(
            payloadJson.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val tokenWithoutSub = "header.$payloadB64.signature"

        assertThrows(IllegalStateException::class.java) {
            GoogleIdTokenParser.extractSubClaim(tokenWithoutSub, json)
        }
    }

    @Test
    fun extractSubClaim_handlesRealisticGoogleSubFormat() {
        // Google `sub` claim — 21-значный numeric string.
        val payloadJson = """{"sub":"108547295013826509471","email":"jane@gmail.com","email_verified":true,"name":"Jane Doe"}"""
        val payloadB64 = Base64.encodeToString(
            payloadJson.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val token = "eyJhbGciOiJSUzI1NiIs.$payloadB64.signature-stuff"

        val sub = GoogleIdTokenParser.extractSubClaim(token, json)
        assertEquals("108547295013826509471", sub)
    }
}
