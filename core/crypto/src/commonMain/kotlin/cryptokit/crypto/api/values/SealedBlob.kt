package cryptokit.crypto.api.values

import kotlin.jvm.JvmInline

/**
 * Output of libsodium `crypto_box_seal` — ephemeralPub(32) + ciphertext + mac(16).
 * Used for ADR-008 social recovery sealed envelopes.
 */
@JvmInline
value class SealedBlob(val bytes: ByteArray)
