package cryptokit.crypto.api

/**
 * Platform-specific construction context for [SecureKeyStore].
 *  • `androidMain` — wraps `android.content.Context`.
 *  • `jvmMain` / `iosMain` — empty.
 */
expect class KeyStoreContext
