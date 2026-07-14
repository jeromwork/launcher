package cryptokit.keys.api.vault

import kotlin.jvm.JvmInline

/**
 * 32-byte Ed25519 public key that names the vault's identity (rule 13 zero-knowledge server
 * verifies signatures against this).
 *
 * Safe to publish — no ownership graph is exposed by the public key alone.
 */
@JvmInline
value class PublicIdentity(val bytes: ByteArray)
