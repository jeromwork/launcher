package cryptokit.keys.impl.vault

/**
 * 32-byte root key material — the single source from which every purpose-key, MAC key, and
 * identity Ed25519 keypair is deterministically derived.
 *
 * `internal` visibility per FR-008: `root_key` bytes MUST NOT cross the vault port boundary.
 * The legacy public `cryptokit.keys.api.RootKey` (from spec 018) is scheduled for downgrade in
 * Phase 4 (T023).
 */
internal class RootKey(internal val bytes: ByteArray) {
    init {
        require(bytes.size == SIZE) { "RootKey must be exactly $SIZE bytes, got ${bytes.size}" }
    }

    override fun toString(): String = "RootKey(***)"

    /** Zero the underlying buffer in place. Subsequent use of [bytes] returns zeros. */
    fun wipe() {
        bytes.fill(0)
    }

    companion object {
        const val SIZE: Int = 32
    }
}
