package family.crypto.fake

import family.crypto.api.values.Ciphertext
import family.crypto.ports.CryptoPort
import family.crypto.ports.GroupId

/**
 * In-memory [CryptoPort] fake (TASK-123, FR-003). **Intentionally insecure** — XOR keystream plus
 * a per-group nonce counter (spec Assumption). It shares epoch state with a [FakeGroupPort] so it
 * exhibits the real invariants:
 *  - two `encryptMessage` calls of the same plaintext yield DIFFERENT ciphertexts (nonce ratchet);
 *  - after the group epoch advances (`mergePendingCommit`), a ciphertext from the prior epoch can
 *    no longer be decrypted — the prior epoch key is unreproducible (forward secrecy);
 *  - decrypting a ciphertext under the wrong group fails (cross-group isolation).
 *
 * Ciphertext layout: `TAG_APPLICATION(1) || nonce(8) || body || mac(16)`. The `TAG_APPLICATION`
 * prefix lets [FakeGroupPort.processMessage] route the same bytes to `ApplicationMessage`.
 */
class FakeCryptoPort(
    private val groups: FakeGroupPort,
) : CryptoPort {

    private val nonceCounters = mutableMapOf<String, Long>()

    override suspend fun encryptMessage(groupId: GroupId, plaintext: ByteArray): Ciphertext {
        val key = groups.currentEpochKey(groupId) // throws on unknown group (deterministic)
        val nonce = nextNonce(groupId)
        val body = xor(plaintext, key, nonce)
        val mac = FakeMlsCodec.mac(body, key, MAC_SIZE)

        val out = ByteArray(1 + NONCE_SIZE + body.size + MAC_SIZE)
        out[0] = FakeMlsCodec.TAG_APPLICATION
        nonce.copyInto(out, 1)
        body.copyInto(out, 1 + NONCE_SIZE)
        mac.copyInto(out, 1 + NONCE_SIZE + body.size)
        return Ciphertext(out)
    }

    override suspend fun decryptMessage(groupId: GroupId, ciphertext: Ciphertext): ByteArray {
        val key = groups.currentEpochKey(groupId)
        val bytes = ciphertext.bytes
        check(bytes.isNotEmpty() && bytes[0] == FakeMlsCodec.TAG_APPLICATION) {
            "Not an application message"
        }
        check(bytes.size >= 1 + NONCE_SIZE + MAC_SIZE) { "Ciphertext too short" }

        val nonce = bytes.copyOfRange(1, 1 + NONCE_SIZE)
        val body = bytes.copyOfRange(1 + NONCE_SIZE, bytes.size - MAC_SIZE)
        val mac = bytes.copyOfRange(bytes.size - MAC_SIZE, bytes.size)

        // Wrong group OR advanced epoch → key mismatch → MAC mismatch → deterministic failure.
        check(mac.contentEquals(FakeMlsCodec.mac(body, key, MAC_SIZE))) {
            "Decryption failed (wrong group or stale epoch)"
        }
        return xor(body, key, nonce)
    }

    private fun nextNonce(groupId: GroupId): ByteArray {
        val n = (nonceCounters[groupId.value] ?: 0L)
        nonceCounters[groupId.value] = n + 1
        val nonce = ByteArray(NONCE_SIZE)
        for (i in 0 until NONCE_SIZE) nonce[i] = (n ushr (i * 8)).toByte()
        return nonce
    }

    private fun xor(input: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val stream = FakeMlsCodec.keystream(key, nonce, input.size)
        val out = ByteArray(input.size)
        for (i in input.indices) out[i] = (input[i].toInt() xor stream[i].toInt()).toByte()
        return out
    }

    private companion object {
        const val NONCE_SIZE = 8
        const val MAC_SIZE = 16
    }
}
