package cryptokit.keys.api.vault

/**
 * Salt derivation hint for [cryptokit.keys.impl.vault.PassphraseRecovery] — determines which
 * Bitwarden-pattern path is used (Session 7 Q-B):
 *
 *  * [GoogleAccount] → `salt = HKDF(googleUid.utf8Bytes, info="salt-v1", 16)`. Deterministic,
 *    no separate storage needed — recovered identically on any new device signed into the
 *    same Google account.
 *  * [NoGmsDevice] → `salt = deviceRandomSalt` (16 bytes CSPRNG stored device-local in Android
 *    Keystore at first setup). Recovery on a fresh device therefore requires an alternative
 *    salt-transfer channel (out of scope for TASK-112 MVP — see server-roadmap).
 *
 * The `googleUid` is *not* leaked to the server as an ownership identifier (rule 13) — it stays
 * client-side as an input to the salt derivation only.
 */
sealed class IdentityHint {
    data class GoogleAccount(val googleUid: String) : IdentityHint()
    data class NoGmsDevice(val deviceRandomSalt: ByteArray) : IdentityHint() {
        init {
            require(deviceRandomSalt.size == SALT_SIZE) {
                "deviceRandomSalt must be $SALT_SIZE bytes, got ${deviceRandomSalt.size}"
            }
        }

        override fun equals(other: Any?): Boolean =
            other is NoGmsDevice && other.deviceRandomSalt.contentEquals(deviceRandomSalt)

        override fun hashCode(): Int = deviceRandomSalt.contentHashCode()
    }

    companion object {
        const val SALT_SIZE: Int = 16
    }
}
