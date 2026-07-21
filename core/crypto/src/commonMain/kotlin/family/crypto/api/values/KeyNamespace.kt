package family.crypto.api.values

/**
 * Allowed namespaces for [KeyId]. Per FR-010 + Clarifications Q2 (spec 016).
 *
 * Each consumer of `:core:crypto` must place its keys under one of these prefixes:
 *  • [Config] — F-5 ConfigDocument encryption keys.
 *  • [Media] — spec 011 media blob encryption keys.
 *  • [Messenger] — future messenger sender-keys / MLS.
 *  • [Recovery] — ADR-008 social recovery escrow keys (future spec, TBD).
 *  • [Internal] — F-CRYPTO internal keys (e.g., AES wrap key in TEE).
 */
sealed class KeyNamespace(val prefix: String) {
    object Config : KeyNamespace("config-")
    object Media : KeyNamespace("media-")
    object Messenger : KeyNamespace("messenger-")
    object Recovery : KeyNamespace("recovery-")
    object Internal : KeyNamespace("__internal-")

    companion object {
        private val all: List<KeyNamespace> = listOf(Config, Media, Messenger, Recovery, Internal)

        fun isValidPrefix(raw: String): Boolean = all.any { raw.startsWith(it.prefix) }

        fun allPrefixes(): List<String> = all.map { it.prefix }
    }
}
