package family.crypto.api.values

import kotlin.jvm.JvmInline

/** 32-byte output of X25519 ECDH. Feed into [family.crypto.api.KeyDerivation]. */
@JvmInline
value class SharedSecret(val bytes: ByteArray)
