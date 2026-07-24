package family.crypto.mls

import androidx.test.ext.junit.runners.AndroidJUnit4
import family.crypto.ports.GroupId
import family.crypto.ports.ProcessedMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test for the real MLS engine (TASK-124, T022 / SC-005).
 *
 * The unit tests already cover the protocol against the HOST cdylib; what only a device can prove is
 * that the cross-compiled `libcrypto_ffi.so` (arm64-v8a, ~1.9 MB with openmls) loads through JNA and
 * runs on Android. Hence a deliberately plain scenario: three members, ten messages, all delivered.
 *
 * **arm64 device required** — `:crypto-ffi` builds arm64-v8a only (TASK-122 Clarification Q5), so an
 * x86_64 emulator cannot run this. Verified on Xiaomi 11T.
 *
 * ```
 * ./gradlew :core:crypto:connectedAndroidTest --tests "*MlsSmokeInstrumentedTest*"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class MlsSmokeInstrumentedTest {

    @Test
    fun threeMemberGroup_encryptsAndDecryptsTenMessages() = runBlocking {
        val chat = GroupId("smoke-chat")
        val alice = OpenMlsStack()
        val bob = OpenMlsStack()
        val carol = OpenMlsStack()

        alice.group.createGroup(chat)

        // Bob joins.
        val addBob = alice.group.addMembers(chat, listOf(bob.keyPackages.generate()))
        alice.group.mergePendingCommit(chat)
        bob.group.joinFromWelcome(requireNotNull(addBob.welcome))

        // Carol joins; Bob follows the commit so all three sit in the same epoch.
        val addCarol = alice.group.addMembers(chat, listOf(carol.keyPackages.generate()))
        alice.group.mergePendingCommit(chat)
        val staged = bob.group.processMessage(chat, addCarol.commit.bytes)
        check(staged is ProcessedMessage.StagedCommit) { "expected a staged commit, got $staged" }
        bob.group.mergePendingCommit(chat)
        carol.group.joinFromWelcome(requireNotNull(addCarol.welcome))

        repeat(10) { index ->
            val plaintext = "message #$index".encodeToByteArray()
            val ciphertext = alice.crypto.encryptMessage(chat, plaintext)
            assertArrayEquals(plaintext, bob.crypto.decryptMessage(chat, ciphertext))
            assertArrayEquals(plaintext, carol.crypto.decryptMessage(chat, ciphertext))
        }
    }
}
