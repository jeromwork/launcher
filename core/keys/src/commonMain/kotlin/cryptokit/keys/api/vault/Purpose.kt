package cryptokit.keys.api.vault

/**
 * Registry of key-material purposes recognised by the [KeyVault] port.
 *
 * Each variant carries:
 *  * [stableId] — 2-byte big-endian id written into the [Ciphertext] blob header. Stable across
 *    enum reordering / rename; changing an id is a wire-format break.
 *  * [algorithm] — cryptographic primitive family the vault uses for this purpose.
 *  * [exportable] — always `false` in MVP. Reserved for a future additive `Purpose.External(...)`
 *    sealed variant (see FR-013 and Decision block "exit ramp" note).
 *  * [rotationPolicy] — declares how new epochs are derived. Enforcement of the policy lives in
 *    a future rotation task; today all values are `LazyOnDemand` or `Manual`.
 *
 * MVP registry — exactly two entries per FR-002. Additional purposes ship as additive variants
 * (never renumber existing ids).
 */
enum class Purpose(
    val stableId: Int,
    val algorithm: Algorithm,
    val exportable: Boolean,
    val rotationPolicy: RotationPolicy,
) {
    CONFIG(
        stableId = 0x0001,
        algorithm = Algorithm.XChaCha20Poly1305,
        exportable = false,
        rotationPolicy = RotationPolicy.LazyOnDemand,
    ),
    RECOVERY_BLOB(
        stableId = 0x0002,
        algorithm = Algorithm.XChaCha20Poly1305,
        exportable = false,
        rotationPolicy = RotationPolicy.Manual,
    ),
    ;

    companion object {
        /**
         * Lookup by [Purpose.stableId] as written in a blob header. Returns `null` for unknown
         * ids so the caller can raise [VaultException.UnsupportedFormatVersion] or
         * [VaultException.WrongPurpose] with precise context.
         */
        fun fromStableId(id: Int): Purpose? = entries.firstOrNull { it.stableId == id }
    }
}

/** Cryptographic primitive family used for a [Purpose]. */
enum class Algorithm {
    /** XChaCha20-Poly1305 AEAD via libsodium `crypto_aead_xchacha20poly1305_ietf_*`. */
    XChaCha20Poly1305,
}

/** How new key epochs are minted for a [Purpose]. Enforcement is deferred to a rotation task. */
enum class RotationPolicy {
    /** New epoch derived on next use after a rotation trigger. */
    LazyOnDemand,

    /** Rotation only when explicitly requested by an admin operation. */
    Manual,
}
