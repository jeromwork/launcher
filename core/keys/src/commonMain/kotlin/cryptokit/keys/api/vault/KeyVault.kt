package cryptokit.keys.api.vault

/**
 * The single misuse-resistant crypto entry-point for the entire domain (TASK-112).
 *
 * See [TASK-112 Decision block](../../../../../../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md)
 * for the port-boundary rationale (operation-on-vault + narrow export). Downstream feature
 * modules (`:core:cloud`, future messenger, album, pairing) MUST route every symmetric
 * seal/open, MAC, and signature through this interface — no direct libsodium usage in feature
 * code (Rule 1 domain isolation, SC-006).
 *
 * ### Lifecycle
 * ```
 * fresh vault ──unlock(strategy)──► ready ──…seal / open / mac / sign…──► ready
 *                                     │
 *                                     └──wipe()──► fresh vault (all subsequent ops throw NoRootKey)
 * ```
 *
 * ### Cross-platform contract (FR-011, SC-004)
 * The wire format ([BlobHeader], canonical AAD, XChaCha20-Poly1305, Ed25519) is fixed so that
 * the Android adapter, JVM/Fake adapter, and future iOS adapter produce byte-equal output for
 * fixed inputs. Anything that changes that byte layout is a major `format_version` bump.
 */
interface KeyVault {

    /**
     * Reconstruct `root_key` via [strategy] and enter the "ready" state. If a root is already
     * cached from a prior `unlock`, this is a no-op (idempotent within a vault session).
     *
     * @throws VaultException.RecoveryFailed if [strategy] rejects the derived root
     *   (e.g. wrong passphrase — FR-006b).
     * @throws VaultException.HardwareBackedKeystoreUnavailable if the platform storage tier is
     *   inaccessible on this device.
     */
    @Throws(VaultException::class)
    suspend fun unlock(strategy: RecoveryStrategy)

    /**
     * Atomically clear `root_key` from both platform storage and in-memory state (Session 7
     * Q-C, FR-006c). Idempotent — safe to call on an already-wiped vault.
     *
     * After `wipe()` every seal/open/mac/sign call throws [VaultException.NoRootKey] until the
     * next successful [unlock].
     */
    suspend fun wipe()

    /**
     * Encrypt [plaintext] under a purpose-specific derived key, authenticating [aad].
     *
     * @throws VaultException.VaultLocked if [unlock] hasn't been called (fresh install / cold start).
     * @throws VaultException.NoRootKey if the vault was wiped since the last unlock.
     */
    @Throws(VaultException::class)
    suspend fun aeadSeal(purpose: Purpose, plaintext: ByteArray, aad: Aad): Ciphertext

    /**
     * Decrypt a [ciphertext] previously produced by [aeadSeal] under the same [purpose] and [aad].
     *
     * @throws VaultException.WrongPurpose if the header purpose id does not match [purpose].
     * @throws VaultException.TamperDetected if the AEAD MAC fails (corruption / wrong key / bad AAD).
     * @throws VaultException.UnsupportedFormatVersion if the blob header carries a version
     *   this build doesn't understand.
     * @throws VaultException.VaultLocked if [unlock] hasn't been called.
     * @throws VaultException.NoRootKey if the vault was wiped since the last unlock.
     */
    @Throws(VaultException::class)
    suspend fun aeadOpen(purpose: Purpose, ciphertext: Ciphertext, aad: Aad): ByteArray

    /**
     * Compute a keyed MAC over [message] using a purpose-specific derived MAC key.
     *
     * Algorithm: keyed BLAKE2b-256 (libsodium `crypto_generichash`) with the derived MAC key.
     *
     * @throws VaultException.VaultLocked if [unlock] hasn't been called.
     * @throws VaultException.NoRootKey if the vault was wiped since the last unlock.
     */
    @Throws(VaultException::class)
    suspend fun mac(purpose: Purpose, message: ByteArray): MacTag

    /**
     * Constant-time verify of a [tag] produced by [mac] over the same [purpose] and [message].
     * NEVER throws on a bad tag — returns `false` (bad tag is a regular control-flow signal).
     *
     * @throws VaultException.VaultLocked if [unlock] hasn't been called.
     * @throws VaultException.NoRootKey if the vault was wiped since the last unlock.
     */
    @Throws(VaultException::class)
    suspend fun verifyMac(purpose: Purpose, message: ByteArray, tag: MacTag): Boolean

    /**
     * Sign [message] with the vault's Ed25519 identity key. Identity-scoped, not purpose-scoped —
     * every KeyVault holds exactly one identity keypair.
     *
     * @throws VaultException.VaultLocked if [unlock] hasn't been called.
     * @throws VaultException.NoRootKey if the vault was wiped since the last unlock.
     */
    @Throws(VaultException::class)
    suspend fun sign(message: ByteArray): Signature

    /**
     * Verify an Ed25519 [signature] against [message] using [publicIdentity]. Pure function —
     * never touches the vault's private state, so it works even on a locked or wiped vault
     * (no exceptions raised beyond the signature/pubkey being malformed).
     */
    suspend fun verify(publicIdentity: PublicIdentity, message: ByteArray, signature: Signature): Boolean

    /**
     * Public half of the vault's identity keypair. Safe to expose (rule 13 zero-knowledge
     * server verifies signatures against this).
     *
     * @throws VaultException.VaultLocked if [unlock] hasn't been called (identity is derived
     *   deterministically from `root_key`).
     * @throws VaultException.NoRootKey if the vault was wiped since the last unlock.
     */
    @Throws(VaultException::class)
    suspend fun publicIdentity(): PublicIdentity
}
