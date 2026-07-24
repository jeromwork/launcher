package family.crypto.mls

import kotlinx.coroutines.test.runTest
import uniffi.crypto_ffi.mlsCreateGroup
import uniffi.crypto_ffi.mlsDecrypt
import uniffi.crypto_ffi.mlsEncrypt
import uniffi.crypto_ffi.mlsGenerateKeyPackage
import uniffi.crypto_ffi.mlsAddMembers
import uniffi.crypto_ffi.mlsJoinFromWelcome
import uniffi.crypto_ffi.mlsMergePendingCommit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Wire-format roundtrip for MLS payloads (TASK-124, T017 / FR-015 / SC-002).
 *
 * The payloads are raw RFC 9420 `MlsMessageOut` bytes — an EXTERNAL wire format. We add no envelope
 * and no `schemaVersion` of our own (spec Clarification #4), so what is asserted here is that the
 * bytes survive the FFI boundary and a Kotlin `ByteArray` copy intact:
 * `encrypt → serialize → deserialize → decrypt == original`.
 */
class MlsMessageRoundtripTest {

    @Test
    fun applicationMessage_roundtripsThroughSerializedBytes() = runTest {
        val group = TwoPartyGroup.create()
        val plaintext = "the quick brown fox jumps over the lazy dog".encodeToByteArray()

        val ciphertext = group.alice.crypto.encryptMessage(group.groupId, plaintext)
        // "Serialize / deserialize": the ciphertext is opaque bytes; copy it as any transport would.
        val transported = ciphertext.bytes.copyOf()
        assertContentEquals(ciphertext.bytes, transported)

        val decrypted = group.bob.crypto.decryptMessage(
            group.groupId,
            family.crypto.api.values.Ciphertext(transported),
        )
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun emptyAndLargePayloads_roundtrip() = runTest {
        val group = TwoPartyGroup.create()

        assertContentEquals(ByteArray(0), group.aliceToBob(ByteArray(0)))

        val large = ByteArray(64 * 1024) { (it % 251).toByte() }
        assertContentEquals(large, group.aliceToBob(large))
    }

    @Test
    fun bothDirections_roundtrip() = runTest {
        val group = TwoPartyGroup.create()
        assertContentEquals("a→b".encodeToByteArray(), group.aliceToBob("a→b".encodeToByteArray()))
        assertContentEquals("b→a".encodeToByteArray(), group.bobToAlice("b→a".encodeToByteArray()))
    }
}

/**
 * The storage snapshot is the state that crosses the FFI boundary on every call (TASK-124, T017).
 *
 * It is an INTERNAL, in-memory serialization — unversioned by design (spec Clarification #3); it
 * becomes a versioned at-rest format only at TASK-125 (SQLCipher). What must hold today is that a
 * snapshot serialized out of Rust and handed straight back in reconstructs an equivalent group:
 * operations performed after the roundtrip produce the same observable result.
 */
class GroupStateRoundtripTest {

    @Test
    fun snapshotRoundtrip_preservesGroupOperations() = runTest {
        val groupId = "g1".encodeToByteArray()

        // Alice creates a group and adds Bob — driving the FFI directly so the snapshot is visible.
        val created = mlsCreateGroup(ByteArray(0), groupId)
        val bobKeyPackage = mlsGenerateKeyPackage(ByteArray(0), false)
        val added = mlsAddMembers(created.state, groupId, listOf(bobKeyPackage.keyPackage))
        val merged = mlsMergePendingCommit(added.state, groupId)

        // Round-trip Alice's snapshot through a plain byte copy (as any store would do).
        val transported = merged.state.copyOf()
        assertContentEquals(merged.state, transported)
        assertTrue(transported.isNotEmpty(), "a live group must produce a non-empty snapshot")

        val bob = mlsJoinFromWelcome(bobKeyPackage.state, added.welcome!!)

        // The operation performed on the round-tripped snapshot behaves identically.
        val encrypted = mlsEncrypt(transported, groupId, "after roundtrip".encodeToByteArray())
        val decrypted = mlsDecrypt(bob.state, groupId, encrypted.ciphertext)
        assertContentEquals("after roundtrip".encodeToByteArray(), decrypted.plaintext)
    }
}
