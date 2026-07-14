package cryptokit.keys.api.vault

import kotlin.jvm.JvmInline

/**
 * Ed25519 signature (64 bytes), identity-scoped.
 *
 * NB: a namesake `cryptokit.crypto.api.values.Signature` exists in `:core:crypto`. The two are
 * intentionally different types — `:core:keys` never exposes the `:core:crypto` value class to
 * downstream feature modules (rule 2 ACL). Adapters bridge between them internally.
 */
@JvmInline
value class Signature(val bytes: ByteArray)
