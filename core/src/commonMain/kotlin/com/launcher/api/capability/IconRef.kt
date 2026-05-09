package com.launcher.api.capability

/**
 * Helpers to build / parse `iconId` strings used in [Capability.iconId].
 *
 * Wire-format string convention per [`contracts/icon-id-namespace.md`](specs/006-provider-capabilities-and-health/contracts/icon-id-namespace.md):
 * `<namespace>:<name>`. `iconId` stays a plain `String` in [Capability] (NOT a
 * sealed class) so wire-format can extend through additional namespaces without
 * migration of existing readers.
 *
 * Reserved namespaces:
 *  - [NAMESPACE_BUNDLED] — built-in provider brand assets in APK (this spec).
 *  - [NAMESPACE_CUSTOM]  — admin-uploaded custom tile icons via Firebase
 *    Storage (claimed by spec 007/009).
 *  - [NAMESPACE_PRIVATE] — e2e-encrypted private media, e.g. contact photos
 *    (claimed by spec 011).
 *
 * Validation through [isValid] is **not** called from [Capability]'s `init` —
 * unknown / invalid namespaces must parse without exception so older readers
 * survive newer producers. Resolution-time fallback handled by [IconStorage].
 */
object IconRef {

    const val NAMESPACE_BUNDLED = "bundled"
    const val NAMESPACE_CUSTOM  = "custom"
    const val NAMESPACE_PRIVATE = "private"

    private val VALID = Regex("^[a-z][a-z0-9_-]*:[A-Za-z0-9_-]{1,128}$")

    /** Build an iconId for a built-in drawable resource. */
    fun bundled(name: String): String = "$NAMESPACE_BUNDLED:$name"

    /** True if [iconId] matches the namespace convention. Used by tests, not by parsers. */
    fun isValid(iconId: String): Boolean = VALID.matches(iconId)

    /** Namespace prefix of [iconId], or null if format is invalid (no `:`). */
    fun namespaceOf(iconId: String): String? =
        iconId.substringBefore(':', missingDelimiterValue = "").takeIf { it.isNotEmpty() && it != iconId }

    /** Name part of [iconId] (after the `:`), or null if format is invalid. */
    fun nameOf(iconId: String): String? =
        iconId.substringAfter(':', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
}
