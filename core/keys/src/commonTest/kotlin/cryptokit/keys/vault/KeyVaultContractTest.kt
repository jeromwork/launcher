package cryptokit.keys.vault

import cryptokit.keys.api.vault.Ciphertext
import cryptokit.keys.api.vault.MacTag
import cryptokit.keys.api.vault.Purpose
import cryptokit.keys.api.vault.TestRecoveryStrategy
import cryptokit.keys.api.vault.VaultException
import cryptokit.keys.api.vault.canonicalAad
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Port-contract tests for [cryptokit.keys.api.vault.KeyVault] — every implementation
 * (`FakeKeyVault`, future `AndroidKeyVault`) MUST pass this suite.
 *
 * Covers FR-001, FR-003, FR-004, FR-006c, SC-004, SC-007, SC-008, SC-012.
 */
class KeyVaultContractTest {

    private fun freshVault() = FakeKeyVault()
    private fun defaultAad() = canonicalAad("ns1", schemaVersion = 1, blobVersion = 1)

    @Test
    fun `aeadSeal aeadOpen roundtrip for CONFIG`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val plaintext = "hello world".encodeToByteArray()
        val ct = vault.aeadSeal(Purpose.CONFIG, plaintext, defaultAad())
        val out = vault.aeadOpen(Purpose.CONFIG, ct, defaultAad())
        assertContentEquals(plaintext, out)
    }

    @Test
    fun `aeadSeal aeadOpen roundtrip for RECOVERY_BLOB`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val plaintext = ByteArray(64) { it.toByte() }
        val ct = vault.aeadSeal(Purpose.RECOVERY_BLOB, plaintext, defaultAad())
        val out = vault.aeadOpen(Purpose.RECOVERY_BLOB, ct, defaultAad())
        assertContentEquals(plaintext, out)
    }

    @Test
    fun `SC-008 wrong purpose rejected on open`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val ct = vault.aeadSeal(Purpose.CONFIG, "x".encodeToByteArray(), defaultAad())
        val ex = assertFailsWith<VaultException.WrongPurpose> {
            vault.aeadOpen(Purpose.RECOVERY_BLOB, ct, defaultAad())
        }
        assertEquals(Purpose.RECOVERY_BLOB, ex.expected)
        assertEquals(Purpose.CONFIG.stableId, ex.actualStableId)
    }

    @Test
    fun `SC-007 tampered ciphertext byte rejected`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val ct = vault.aeadSeal(Purpose.CONFIG, "hello".encodeToByteArray(), defaultAad())
        val tampered = ct.bytes.copyOf()
        // Flip a byte in the middle of the AEAD payload (past the header).
        val idx = tampered.size - 5
        tampered[idx] = (tampered[idx].toInt() xor 0xFF).toByte()
        assertFailsWith<VaultException.TamperDetected> {
            vault.aeadOpen(Purpose.CONFIG, Ciphertext(tampered), defaultAad())
        }
    }

    @Test
    fun `SC-007 tampered AAD rejected`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val ct = vault.aeadSeal(Purpose.CONFIG, "hello".encodeToByteArray(), canonicalAad("ns1", 1, 1))
        val wrongAad = canonicalAad("ns2", 1, 1)
        assertFailsWith<VaultException.TamperDetected> {
            vault.aeadOpen(Purpose.CONFIG, ct, wrongAad)
        }
    }

    @Test
    fun `mac verifyMac roundtrip`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val msg = "authenticate me".encodeToByteArray()
        val tag = vault.mac(Purpose.CONFIG, msg)
        assertTrue(vault.verifyMac(Purpose.CONFIG, msg, tag))
        assertFalse(vault.verifyMac(Purpose.CONFIG, "different message".encodeToByteArray(), tag))
        val badTag = MacTag(ByteArray(tag.bytes.size))
        assertFalse(vault.verifyMac(Purpose.CONFIG, msg, badTag))
    }

    @Test
    fun `mac isolates by purpose`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val msg = "same message".encodeToByteArray()
        val tagConfig = vault.mac(Purpose.CONFIG, msg)
        val tagRecovery = vault.mac(Purpose.RECOVERY_BLOB, msg)
        assertFalse(tagConfig.bytes.contentEquals(tagRecovery.bytes))
        assertFalse(vault.verifyMac(Purpose.RECOVERY_BLOB, msg, tagConfig))
    }

    @Test
    fun `sign verify roundtrip and negative`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val msg = "signed message".encodeToByteArray()
        val sig = vault.sign(msg)
        val pub = vault.publicIdentity()
        assertTrue(vault.verify(pub, msg, sig))
        val wrongMsg = "tampered message".encodeToByteArray()
        assertFalse(vault.verify(pub, wrongMsg, sig))
    }

    @Test
    fun `identity is deterministic from root`() = runTest {
        val vaultA = freshVault()
        val vaultB = freshVault()
        vaultA.unlock(TestRecoveryStrategy())
        vaultB.unlock(TestRecoveryStrategy())
        assertContentEquals(vaultA.publicIdentity().bytes, vaultB.publicIdentity().bytes)
    }

    @Test
    fun `VaultLocked before unlock`() = runTest {
        val vault = freshVault()
        assertFailsWith<VaultException.VaultLocked> {
            vault.aeadSeal(Purpose.CONFIG, "x".encodeToByteArray(), defaultAad())
        }
    }

    @Test
    fun `SC-012 wipe cascade — subsequent op throws NoRootKey`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val ct = vault.aeadSeal(Purpose.CONFIG, "hello".encodeToByteArray(), defaultAad())
        vault.wipe()
        assertFailsWith<VaultException.NoRootKey> {
            vault.aeadOpen(Purpose.CONFIG, ct, defaultAad())
        }
        assertFailsWith<VaultException.NoRootKey> {
            vault.sign("x".encodeToByteArray())
        }
    }

    @Test
    fun `wipe is idempotent`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        vault.wipe()
        vault.wipe() // no throw
        assertFailsWith<VaultException.NoRootKey> {
            vault.publicIdentity()
        }
    }

    @Test
    fun `re-unlock after wipe restores identity`() = runTest {
        val vault = freshVault()
        vault.unlock(TestRecoveryStrategy())
        val pubBefore = vault.publicIdentity().bytes.copyOf()
        vault.wipe()
        vault.unlock(TestRecoveryStrategy())
        val pubAfter = vault.publicIdentity().bytes
        assertContentEquals(pubBefore, pubAfter)
    }

    @Test
    fun `FR-006b RecoveryFailed at unlock`() = runTest {
        val vault = freshVault()
        assertFailsWith<VaultException.RecoveryFailed> {
            vault.unlock(TestRecoveryStrategy(rejectVerify = true))
        }
        // Vault remains locked after failed unlock.
        assertFailsWith<VaultException.VaultLocked> {
            vault.publicIdentity()
        }
    }

    @Test
    fun `different root yields different identity`() = runTest {
        val v1 = freshVault(); val v2 = freshVault()
        v1.unlock(TestRecoveryStrategy(fakeRoot = ByteArray(32) { 1 }))
        v2.unlock(TestRecoveryStrategy(fakeRoot = ByteArray(32) { 2 }))
        assertNotEquals(v1.publicIdentity().bytes.toList(), v2.publicIdentity().bytes.toList())
    }
}
