package com.launcher.adapters.auth

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure-Kotlin helper для разбора Google ID Token. Вынесен из
 * [GoogleSignInAuthAdapter], чтобы быть unit-testable без mock'инга
 * Context / FirebaseAuth / Firestore / CredentialManager.
 *
 * Per spec 017 T750, FR-016a.
 */
internal object GoogleIdTokenParser {

    /**
     * Decode Google ID Token middle segment (Base64url, no padding),
     * extract `sub` claim. Используется как providerAccountId для lookup
     * identity-links.
     *
     * @throws IllegalArgumentException если token не имеет >=2 сегментов.
     * @throws IllegalStateException если payload не содержит `sub` поля.
     */
    fun extractSubClaim(idToken: String, json: Json = Json { ignoreUnknownKeys = true }): String {
        val parts = idToken.split('.')
        require(parts.size >= 2) { "Malformed Google ID Token: <2 segments" }
        val payloadBytes = Base64.decode(
            parts[1],
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val payloadJson = json.parseToJsonElement(String(payloadBytes, Charsets.UTF_8)) as JsonObject
        return payloadJson["sub"]?.jsonPrimitive?.content
            ?: error("Google ID Token payload missing 'sub' claim")
    }
}
