package family.crypto.ports

import family.crypto.fake.FakeCryptoPort
import family.crypto.fake.FakeGroupPort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Executable mirror of the consumer snippet in `core/crypto/README.md` (TASK-123, FR-010,
 * SC-001/SC-004). Proves a consumer can drive `createGroup → addMembers → encrypt → decrypt`
 * end-to-end on the fakes with zero external dependencies. If this test and the README drift, the
 * README is wrong — this is the source of truth.
 */
class ConsumerUsageExampleTest {

    @Test
    fun createGroup_addMember_encrypt_decrypt_onFakes() = runTest {
        val group = FakeGroupPort()
        val crypto = FakeCryptoPort(group) // shares epoch state with the group

        val chat = GroupId("family-chat")
        group.createGroup(chat)

        val bundle = group.addMembers(chat, listOf(KeyPackage("alice-key-package".encodeToByteArray())))
        assertEquals(true, bundle.commit.bytes.isNotEmpty())
        group.mergePendingCommit(chat)

        val envelope = crypto.encryptMessage(chat, "hello".encodeToByteArray())
        val received = crypto.decryptMessage(chat, envelope)
        assertEquals("hello", received.decodeToString())
    }
}
