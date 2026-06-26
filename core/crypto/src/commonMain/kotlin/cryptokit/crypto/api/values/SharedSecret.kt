package cryptokit.crypto.api.values

import kotlin.jvm.JvmInline

/** 32-byte output of X25519 ECDH. Feed into [cryptokit.crypto.api.KeyDerivation]. */
@JvmInline
value class SharedSecret(val bytes: ByteArray)
