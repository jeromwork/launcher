package com.launcher.adapters.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.launcher.api.crypto.HASH_OUTPUT_SIZE
import com.launcher.api.crypto.HashFunction

// BLAKE2b-256 (32-byte output) через libsodium crypto_generichash.
// keyless mode (key = null, keylen = 0) — обычное cryptographic hash.
internal class LibsodiumHashFunction(
    private val sodium: LazySodiumAndroid = LibsodiumProvider.sodium,
) : HashFunction {

    override fun hash(data: ByteArray): ByteArray {
        val out = ByteArray(HASH_OUTPUT_SIZE)
        val ok = sodium.cryptoGenericHash(
            out,
            HASH_OUTPUT_SIZE,
            data,
            data.size.toLong(),
            null,
            0,
        )
        check(ok) { "crypto_generichash failed" }
        return out
    }
}
