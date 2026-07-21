package family.crypto.libsodium

/**
 * Platform-provided HMAC-SHA256 used by [LibsodiumKeyDerivation] for RFC 5869 HKDF.
 * ionspin libsodium 0.9.5 does not expose `crypto_auth_hmacsha256` in the common bindings,
 * so we use platform crypto (JDK `Mac`, iOS `CommonCrypto`) via expect/actual.
 *
 * iOS actual is intentionally a stub-screamer — same policy as [family.crypto.api.SecureKeyStore]
 * (FR-002 + Clarifications Q1). HKDF on iOS will be wired when V-1 ships.
 */
internal expect object HmacSha256 {
    fun mac(key: ByteArray, data: ByteArray): ByteArray
}
