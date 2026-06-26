package cryptokit.crypto.api.values

import kotlin.jvm.JvmInline

/** Ed25519 detached signature — 64 bytes. */
@JvmInline
value class Signature(val bytes: ByteArray)
