package family.crypto.mls

import family.crypto.ports.GroupId
import family.crypto.ports.ProcessedMessage
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Property-based sweep over random operation sequences (TASK-124, T018 / SC-004).
 *
 * 100 random programs of `add` / `encrypt` / `remove` / `selfUpdate` are run against the real
 * engine. The invariants asserted after every step:
 *  - no unexpected exception (a crash or state-machine dead-end shows up here, not in production);
 *  - every peer still in the group decrypts what the group creator sends;
 *  - a removed peer is gone for good (its ciphertexts are no longer readable by it).
 */
class MlsPropertyTest {

    private enum class Op { ADD, ENCRYPT, REMOVE, SELF_UPDATE }

    private val programs: Arb<List<Op>> = arbitrary { rs ->
        val length = Arb.int(1..8).bind()
        List(length) { Op.entries[rs.random.nextInt(Op.entries.size)] }
    }

    @Test
    fun randomOperationSequences_preserveGroupInvariants() = runTest {
        var sequence = 0
        checkAll(PropTestConfig(iterations = 100, seed = 124L), programs) { program ->
            val groupId = GroupId("prop-${sequence++}")
            val creator = OpenMlsStack()
            creator.group.createGroup(groupId)

            // Peers currently in the group: their stack plus the identity the roster knows.
            val members = mutableListOf<Pair<OpenMlsStack, family.crypto.ports.IdentityKey>>()

            for (op in program) {
                when (op) {
                    Op.ADD -> {
                        val peer = OpenMlsStack()
                        val generated = peer.keyPackages.generateWithIdentity()
                        val bundle = creator.group.addMembers(groupId, listOf(generated.keyPackage))
                        creator.group.mergePendingCommit(groupId)
                        // Existing members follow the commit; the newcomer joins via the Welcome.
                        members.forEach { (stack, _) ->
                            val processed = stack.group.processMessage(groupId, bundle.commit.bytes)
                            check(processed is ProcessedMessage.StagedCommit)
                            stack.group.mergePendingCommit(groupId)
                        }
                        peer.group.joinFromWelcome(requireNotNull(bundle.welcome))
                        members += peer to generated.identity
                    }

                    Op.ENCRYPT -> {
                        val plaintext = "msg-${members.size}".encodeToByteArray()
                        val ciphertext = creator.crypto.encryptMessage(groupId, plaintext)
                        members.forEach { (stack, _) ->
                            assertContentEquals(
                                plaintext,
                                stack.crypto.decryptMessage(groupId, ciphertext),
                            )
                        }
                    }

                    Op.REMOVE -> {
                        val victim = members.removeLastOrNull() ?: continue
                        val bundle = creator.group.removeMembers(groupId, listOf(victim.second))
                        creator.group.mergePendingCommit(groupId)
                        members.forEach { (stack, _) ->
                            stack.group.processMessage(groupId, bundle.commit.bytes)
                            stack.group.mergePendingCommit(groupId)
                        }
                        // The removed peer no longer follows the group.
                        val afterRemoval = creator.crypto.encryptMessage(
                            groupId,
                            "post-removal".encodeToByteArray(),
                        )
                        runCatching { victim.first.crypto.decryptMessage(groupId, afterRemoval) }
                            .onSuccess { error("a removed member must not decrypt later messages") }
                    }

                    Op.SELF_UPDATE -> {
                        val bundle = creator.group.selfUpdate(groupId)
                        creator.group.mergePendingCommit(groupId)
                        members.forEach { (stack, _) ->
                            stack.group.processMessage(groupId, bundle.commit.bytes)
                            stack.group.mergePendingCommit(groupId)
                        }
                    }
                }
            }
        }
    }
}
