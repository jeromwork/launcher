package family.keys.contracts

import family.keys.api.Outcome
import family.keys.api.PassphraseKdfParams
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.api.BackupError
import family.keys.fakes.FakeRecoveryKeyBackup
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Contract test для [family.keys.api.RecoveryKeyBackup] (T031, FR-008).
 */
class RecoveryKeyBackupContractTest {

    private fun sampleBlob(seed: Byte = 0x42) = RecoveryKeyBackupBlob(
        kdfSalt = ByteArray(16) { seed },
        kdfParams = PassphraseKdfParams(),
        wrappedRootKey = ByteArray(48) { (seed + it).toByte() },
        nonce = ByteArray(24) { (seed - it).toByte() },
        createdAt = 1_700_000_000L
    )

    @Test
    fun storeThenFetchReturnsEqual() = runTest {
        val backup = FakeRecoveryKeyBackup()
        val uid = "user-uid-1"
        val blob = sampleBlob()

        val store = backup.uploadBlob(uid, blob)
        assertIs<Outcome.Success<Unit>>(store)

        val fetch = backup.fetchBlob(uid)
        assertIs<Outcome.Success<RecoveryKeyBackupBlob>>(fetch)
        assertEquals(blob, fetch.value)
    }

    @Test
    fun fetchUnknownUidReturnsNotFound() = runTest {
        val backup = FakeRecoveryKeyBackup()
        val fetch = backup.fetchBlob("nonexistent-uid")
        assertIs<Outcome.Failure<BackupError>>(fetch)
        assertEquals(BackupError.NotFound, fetch.error)
    }

    @Test
    fun deleteRemovesVault() = runTest {
        val backup = FakeRecoveryKeyBackup()
        val uid = "user-uid-1"
        backup.uploadBlob(uid, sampleBlob())

        val del = backup.deleteBlob(uid)
        assertIs<Outcome.Success<Unit>>(del)

        val fetch = backup.fetchBlob(uid)
        assertIs<Outcome.Failure<BackupError>>(fetch)
        assertEquals(BackupError.NotFound, fetch.error)
    }

    @Test
    fun multipleUidsAreIsolated() = runTest {
        val backup = FakeRecoveryKeyBackup()
        val blob1 = sampleBlob(0x11)
        val blob2 = sampleBlob(0x22)
        backup.uploadBlob("uid-1", blob1)
        backup.uploadBlob("uid-2", blob2)

        val f1 = backup.fetchBlob("uid-1") as Outcome.Success<RecoveryKeyBackupBlob>
        val f2 = backup.fetchBlob("uid-2") as Outcome.Success<RecoveryKeyBackupBlob>
        assertEquals(blob1, f1.value)
        assertEquals(blob2, f2.value)
    }
}
