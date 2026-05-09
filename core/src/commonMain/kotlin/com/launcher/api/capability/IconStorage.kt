package com.launcher.api.capability

/**
 * Port (interface) resolving an `iconId` string to a renderable [IconResolution].
 *
 * Per FR-008, FR-009. In spec 006 there is exactly one implementation:
 *  - `BundledIconStorage` (androidMain) — looks up `bundled:<name>` against APK
 *    drawable resources; returns [IconResolution.Placeholder] for `custom:`/
 *    `private:` (claimed by future specs); returns [IconResolution.NotFound]
 *    for unknown namespaces or invalid format.
 *
 * Spec 007 adds `RemoteIconStorage` (Firebase Storage + LRU cache);
 * spec 011 adds `EncryptedMediaStorage` for private namespace.
 *
 * Implementations MUST be **pure** for the bundled namespace (no I/O — name →
 * resource id is a constant-time map lookup) so resolution can happen during
 * Composable rendering without main-thread risk.
 */
interface IconStorage {
    fun resolve(iconId: String): IconResolution
}
