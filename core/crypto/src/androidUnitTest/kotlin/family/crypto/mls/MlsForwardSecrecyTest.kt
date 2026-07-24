package family.crypto.mls

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Forward-secrecy assertions on the real engine (TASK-124, T019 / SC-003).
 *
 * These are the properties MLS exists for, and the fake could only imitate them:
 *  - the ratchet moves, so the same plaintext never yields the same ciphertext twice;
 *  - once the group advances an epoch, ciphertexts from the previous epoch are unreadable — the
 *    old key material is gone, not merely ignored.
 */
class MlsForwardSecrecyTest {

    @Test
    fun sameplaintextTwice_yieldsDifferentCiphertexts() = runTest {
        val group = TwoPartyGroup.create()
        val plaintext = "same".encodeToByteArray()

        val first = group.alice.crypto.encryptMessage(group.groupId, plaintext)
        val second = group.alice.crypto.encryptMessage(group.groupId, plaintext)

        assertFalse(
            first.bytes.contentEquals(second.bytes),
            "the ratchet must advance — identical ciphertexts would mean key reuse",
        )
        // Both are still readable by the peer in the current epoch.
        assertContentEquals(plaintext, group.bob.crypto.decryptMessage(group.groupId, first))
        assertContentEquals(plaintext, group.bob.crypto.decryptMessage(group.groupId, second))
    }

    @Test
    fun priorEpochCiphertext_isUndecryptableAfterEpochChange() = runTest {
        val group = TwoPartyGroup.create()

        // Encrypted in epoch N, deliberately NOT delivered yet.
        val stale = group.alice.crypto.encryptMessage(
            group.groupId,
            "old epoch secret".encodeToByteArray(),
        )

        // Both members advance to epoch N+1.
        group.aliceSelfUpdateAndSync()

        assertFailsWith<Exception> { group.bob.crypto.decryptMessage(group.groupId, stale) }

        // The new epoch still works — the group is healthy, not broken.
        assertContentEquals(
            "new epoch".encodeToByteArray(),
            group.aliceToBob("new epoch".encodeToByteArray()),
        )
    }

    @Test
    fun removedMember_cannotReadSubsequentMessages() = runTest {
        val group = TwoPartyGroup.create()
        val carol = OpenMlsStack()

        // Alice adds Carol, everyone syncs.
        val carolKeyPackage = carol.keyPackages.generateWithIdentity()
        val addBundle = group.alice.group.addMembers(
            group.groupId,
            listOf(carolKeyPackage.keyPackage),
        )
        group.alice.group.mergePendingCommit(group.groupId)
        group.bob.group.processMessage(group.groupId, addBundle.commit.bytes)
        group.bob.group.mergePendingCommit(group.groupId)
        carol.group.joinFromWelcome(requireNotNull(addBundle.welcome))

        assertContentEquals(
            "before removal".encodeToByteArray(),
            carol.crypto.decryptMessage(
                group.groupId,
                group.alice.crypto.encryptMessage(group.groupId, "before removal".encodeToByteArray()),
            ),
        )

        // Alice removes Carol (by the identity bound to the KeyPackage she was added with) and the
        // remaining members advance.
        val removeBundle =
            group.alice.group.removeMembers(group.groupId, listOf(carolKeyPackage.identity))
        group.alice.group.mergePendingCommit(group.groupId)
        group.bob.group.processMessage(group.groupId, removeBundle.commit.bytes)
        group.bob.group.mergePendingCommit(group.groupId)

        val afterRemoval = group.alice.crypto.encryptMessage(
            group.groupId,
            "after removal".encodeToByteArray(),
        )
        assertFailsWith<Exception> { carol.crypto.decryptMessage(group.groupId, afterRemoval) }
    }
}
