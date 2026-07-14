package cryptokit.keys.api.vault

/**
 * Pluggable adapter that reconstructs the vault's 32-byte `root_key` (FR-005). Adding a new
 * strategy (BIP39, 2FA, social recovery) MUST be additive — [KeyVault] and its callers stay
 * untouched.
 *
 * ### Sealed on purpose
 * `sealed class` so all inheritors live inside `:core:keys` — feature modules can *construct*
 * existing adapters (e.g. `PassphraseRecovery(...)`) but MUST NOT invent their own. Two reasons:
 *
 *  1. Only the vault adapter should ever invoke [deriveRoot] / [verifyUnlock] — sealing lets
 *     us declare them `internal` and rely on the compiler, not convention (Rule 1 + Rule 2).
 *  2. New recovery mechanisms are additive changes inside this module — the port surface never
 *     bends to accommodate them. Consistent with the "one-way door with narrow surface"
 *     principle (Rule 3).
 *
 * ### `verifyUnlock` hook (Session 7 Q-D D1)
 * After the adapter derives a candidate root, the vault calls [verifyUnlock] so the adapter
 * can validate the guess (e.g. `PassphraseRecovery` opens a known-plaintext validation blob).
 * Failure MUST raise [VaultException.RecoveryFailed] — otherwise the caller only sees a
 * [VaultException.TamperDetected] on first data access, which is a worse UX signal (FR-006b).
 *
 * An adapter that cannot pre-validate MAY leave [verifyUnlock] as a no-op — deliberate weaker mode.
 */
abstract class RecoveryStrategy {
    /**
     * Derive the 32-byte root key material from whatever secret the strategy holds
     * (passphrase, seed words, hardware token, …). Vault owns the returned buffer and MUST wipe it.
     *
     * `suspend` because real implementations (Argon2id, HKDF) call the `:core:crypto` port,
     * which is `suspend fun`.
     *
     * **Only `KeyVault` implementations should call this method.** It cannot be marked `internal`
     * because Kotlin/MPP treats `commonTest` as a separate module from `commonMain`, which would
     * block `TestRecoveryStrategy` extension. Enforcement is by convention + Rule 1 (feature
     * modules import ports and construct strategies, never invoke `deriveRoot` directly).
     */
    abstract suspend fun deriveRoot(): ByteArray

    /**
     * Optional post-derive check that the recovered root is the correct one for this identity.
     * Called by the vault after [deriveRoot], before the root is stored / used.
     *
     * Same convention-only visibility caveat as [deriveRoot]. See KDoc there.
     *
     * @throws VaultException.RecoveryFailed if the candidate is provably wrong.
     */
    abstract suspend fun verifyUnlock(candidateRoot: ByteArray)
}
