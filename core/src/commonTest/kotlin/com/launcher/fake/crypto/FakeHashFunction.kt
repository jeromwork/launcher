package com.launcher.fake.crypto

import com.launcher.api.crypto.HASH_OUTPUT_SIZE
import com.launcher.api.crypto.HashFunction

// Deterministic XOR-fold hash. НЕ криптография — collision-prone. Только для тестов.
class FakeHashFunction : HashFunction {
    override fun hash(data: ByteArray): ByteArray {
        val out = ByteArray(HASH_OUTPUT_SIZE)
        for (i in data.indices) {
            out[i % HASH_OUTPUT_SIZE] = (out[i % HASH_OUTPUT_SIZE].toInt() xor data[i].toInt()).toByte()
        }
        // Avalanche: rotate-and-mix второй проход.
        for (i in 0 until HASH_OUTPUT_SIZE) {
            val v = out[i].toInt() and 0xFF
            out[i] = (((v shl 3) or (v ushr 5)) and 0xFF).toByte()
        }
        return out
    }
}
