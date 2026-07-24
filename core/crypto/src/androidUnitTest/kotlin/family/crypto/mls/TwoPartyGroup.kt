package family.crypto.mls

import family.crypto.ports.GroupId
import family.crypto.ports.ProcessedMessage

/**
 * Two devices in one real MLS group — the smallest setup in which MLS semantics can actually be
 * observed (TASK-124 test support).
 *
 * A single-member group cannot demonstrate encrypt→decrypt or forward secrecy: RFC 9420 forbids a
 * member from unprotecting its own message. So the roundtrip, property and forward-secrecy tests
 * all run against this pair: [alice] creates the group and adds [bob] from a genuine KeyPackage,
 * [bob] joins through the Welcome.
 */
internal class TwoPartyGroup(
    val alice: OpenMlsStack,
    val bob: OpenMlsStack,
    val groupId: GroupId,
) {
    /** Send from [alice] to [bob], returning the plaintext [bob] recovered. */
    suspend fun aliceToBob(plaintext: ByteArray): ByteArray {
        val ciphertext = alice.crypto.encryptMessage(groupId, plaintext)
        return bob.crypto.decryptMessage(groupId, ciphertext)
    }

    /** Send from [bob] to [alice], returning the plaintext [alice] recovered. */
    suspend fun bobToAlice(plaintext: ByteArray): ByteArray {
        val ciphertext = bob.crypto.encryptMessage(groupId, plaintext)
        return alice.crypto.decryptMessage(groupId, ciphertext)
    }

    /** Alice rotates her leaf key; Bob follows the commit so both land in the new epoch. */
    suspend fun aliceSelfUpdateAndSync() {
        val bundle = alice.group.selfUpdate(groupId)
        alice.group.mergePendingCommit(groupId)
        val processed = bob.group.processMessage(groupId, bundle.commit.bytes)
        check(processed is ProcessedMessage.StagedCommit) { "expected a staged commit, got $processed" }
        bob.group.mergePendingCommit(groupId)
    }

    companion object {
        suspend fun create(id: String = "g1"): TwoPartyGroup {
            val alice = OpenMlsStack()
            val bob = OpenMlsStack()
            val groupId = GroupId(id)

            alice.group.createGroup(groupId)
            val bobKeyPackage = bob.keyPackages.generate()
            val bundle = alice.group.addMembers(groupId, listOf(bobKeyPackage))
            alice.group.mergePendingCommit(groupId)

            val welcome = requireNotNull(bundle.welcome) { "adding a member must produce a Welcome" }
            val joined = bob.group.joinFromWelcome(welcome)
            check(joined == groupId) { "joined $joined, expected $groupId" }

            return TwoPartyGroup(alice, bob, groupId)
        }
    }
}
