package family.crypto.api.values

import kotlin.jvm.JvmInline

/**
 * Strongly-typed key identifier with prefix validation (FR-010 + Clarifications Q2).
 *
 * Format: `<namespace-prefix><kebab-case-suffix>` — all lowercase ASCII, hyphens only.
 *
 * Examples:
 *  • `config-admin-identity-v1` — Config namespace.
 *  • `media-photo-album-v1` — Media namespace.
 *  • `__internal-wrap-key-v1` — F-CRYPTO internal namespace.
 *
 * Invalid:
 *  • `photo-album-v1` — no recognised prefix.
 *  • `Config-Admin-V1` — uppercase letters not allowed.
 *  • empty string.
 *
 * Per FR-010 — compile-time `value class` wrapper prevents string typos at use sites.
 */
@JvmInline
value class KeyId(val raw: String) {
    init {
        require(raw.isNotEmpty()) { "KeyId must not be empty" }
        require(KeyNamespace.isValidPrefix(raw)) {
            "Invalid KeyId '$raw': must start with one of ${KeyNamespace.allPrefixes()}"
        }
        require(raw.matches(KEBAB_CASE)) {
            "Invalid KeyId '$raw': must be kebab-case ASCII (lowercase letters, digits, hyphens, single leading '__' allowed)"
        }
    }

    companion object {
        // Allow leading `__internal-` namespace, then kebab-case lowercase ASCII.
        private val KEBAB_CASE = Regex("^_{0,2}[a-z][a-z0-9]*(-[a-z0-9]+)+$")
    }
}
