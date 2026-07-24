package family.crypto.mls

import family.crypto.api.values.Ciphertext
import family.crypto.ports.CryptoPort
import family.crypto.ports.GroupId
import uniffi.crypto_ffi.mlsDecrypt
import uniffi.crypto_ffi.mlsEncrypt

/**
 * Real [CryptoPort] over openmls 0.8.1 (TASK-124, T012) — MLS application messages inside an
 * established group.
 *
 * Shares its [MlsSnapshotStore] with the [OpenMlsGroupPort] that owns the group's epoch state; that
 * is the whole point of the shared-store constructor, and [CryptoPortContract] asserts it.
 *
 * A ciphertext produced here is a raw RFC 9420 `MlsMessageOut` — an external wire format, carrying
 * no envelope or `schemaVersion` of ours (spec Clarification #4). [Ciphertext] is reused verbatim
 * from `family.crypto.api.values` (FR-006), it is just a byte handle.
 */
class OpenMlsCryptoPort internal constructor(
    private val store: MlsSnapshotStore,
) : CryptoPort {

    override suspend fun encryptMessage(groupId: GroupId, plaintext: ByteArray): Ciphertext =
        store.mutate { state ->
            val result = mlsEncrypt(state, groupId.toBytes(), plaintext)
            result.state to Ciphertext(result.ciphertext)
        }

    override suspend fun decryptMessage(groupId: GroupId, ciphertext: Ciphertext): ByteArray =
        store.mutate { state ->
            val result = mlsDecrypt(state, groupId.toBytes(), ciphertext.bytes)
            result.state to result.plaintext
        }
}
