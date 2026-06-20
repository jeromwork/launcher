package family.keys.contracts

import family.keys.api.Outcome
import family.keys.api.PassphraseKdfParams
import family.keys.api.RecoveryVaultBlob
import family.keys.api.VaultError
import family.keys.fakes.FakeRecoveryKeyVault
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Contract test для [family.keys.api.RecoveryKeyVault] (T031, FR-008).
 */
class RecoveryKeyVaultContractTest {

    private fun sampleBlob(seed: Byte = 0x42) = RecoveryVaultBlob(
        kdfSalt = ByteArray(16) { seed },
        kdfParams = PassphraseKdfParams(),
        wrappedRootKey = ByteArray(48) { (seed + it).toByte() },
        nonce = ByteArray(24) { (seed - it).toByte() },
        createdAt = 1_700_000_000L
    )

    @Test
    fun storeThenFetchReturnsEqual() = runTest {
        val vault = FakeRecoveryKeyVault()
        val uid = "user-uid-1"
        val blob = sampleBlob()

        val store = vault.storeVault(uid, blob)
        assertIs<Outcome.Success<Unit>>(store)

        val fetch = vault.fetchVault(uid)
        assertIs<Outcome.Success<RecoveryVaultBlob>>(fetch)
        assertEquals(blob, fetch.value)
    }

    @Test
    fun fetchUnknownUidReturnsNotFound() = runTest {
        val vault = FakeRecoveryKeyVault()
        val fetch = vault.fetchVault("nonexistent-uid")
        assertIs<Outcome.Failure<VaultError>>(fetch)
        assertEquals(VaultError.NotFound, fetch.error)
    }

    @Test
    fun deleteRemovesVault() = runTest {
        val vault = FakeRecoveryKeyVault()
        val uid = "user-uid-1"
        vault.storeVault(uid, sampleBlob())

        val del = vault.deleteVault(uid)
        assertIs<Outcome.Success<Unit>>(del)

        val fetch = vault.fetchVault(uid)
        assertIs<Outcome.Failure<VaultError>>(fetch)
        assertEquals(VaultError.NotFound, fetch.error)
    }

    @Test
    fun multipleUidsAreIsolated() = runTest {
        val vault = FakeRecoveryKeyVault()
        val blob1 = sampleBlob(0x11)
        val blob2 = sampleBlob(0x22)
        vault.storeVault("uid-1", blob1)
        vault.storeVault("uid-2", blob2)

        val f1 = vault.fetchVault("uid-1") as Outcome.Success<RecoveryVaultBlob>
        val f2 = vault.fetchVault("uid-2") as Outcome.Success<RecoveryVaultBlob>
        assertEquals(blob1, f1.value)
        assertEquals(blob2, f2.value)
    }
}
