package cryptokit.keys.api.vault

/**
 * Sealed hierarchy of every failure the [KeyVault] port can raise. Exhaustive `when` on this
 * class MUST cover all seven variants without an `else` branch (SC coverage in
 * `VaultExceptionExhaustivenessTest`).
 *
 * Rationale for sealed exception rather than `Outcome<T, VaultException>` — see Session 2 Q3:
 * Kotlin-idiomatic + FFI-friendly + orthogonal to TASK-113 Outcome refactor.
 */
sealed class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Any operation invoked before a successful [KeyVault.unlock]. */
    class VaultLocked(message: String = "Vault is locked; call unlock(strategy) first") :
        VaultException(message)

    /**
     * Ciphertext header carries a purpose that does not match the caller's request.
     * Second-line defence against cross-purpose confusion (SC-008).
     */
    class WrongPurpose(val expected: Purpose, val actualStableId: Int) :
        VaultException(
            "Wrong purpose: expected=${expected.name}(id=0x${expected.stableId.toString(16)}), " +
                "actual header id=0x${actualStableId.toString(16)}",
        )

    /** AEAD MAC failure — corrupted ciphertext, tampered AAD, or wrong key. */
    class TamperDetected(message: String = "AEAD MAC mismatch — data tampered or wrong key") :
        VaultException(message)

    /** Header carries a format_version this build does not understand. */
    class UnsupportedFormatVersion(val version: Int) :
        VaultException("Unsupported blob format_version=$version")

    /** [KeyVault.wipe] has removed the root key; caller must run a fresh [KeyVault.unlock]. */
    class NoRootKey(message: String = "No root key present; run unlock(strategy) after wipe") :
        VaultException(message)

    /** Platform storage tier (Android Keystore / iOS Keychain / …) is not available. */
    class HardwareBackedKeystoreUnavailable(message: String, cause: Throwable? = null) :
        VaultException(message, cause)

    /**
     * The supplied [RecoveryStrategy] rejected the unlock attempt (e.g. wrong passphrase).
     * Not a silent [TamperDetected] on first data access — surfaces at unlock time (FR-006b).
     */
    class RecoveryFailed(message: String = "Recovery strategy rejected unlock", cause: Throwable? = null) :
        VaultException(message, cause)
}
