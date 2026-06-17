package com.launcher.api.localization

/**
 * String resolver port — per FR-027.
 *
 * Resolution chain (per FR-029): requested locale → EN base → key literal.
 *
 * Real adapter: `ComposeResourceStringResolver` in :app (binds to Compose
 * Multiplatform Resources `Res.string.*`).
 * Fake adapter: `FakeStringResolver` in commonTest.
 */
interface StringResolver {
    fun resolve(key: String, args: Map<String, Any> = emptyMap()): String
    fun resolvePlural(key: String, count: Int, args: Map<String, Any> = emptyMap()): String
    fun currentLocaleTag(): String
}
