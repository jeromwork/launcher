package com.launcher.adapters.crypto

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.launcher.adapters.crypto.db.CryptoStore
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.EncryptedEnvelope
import com.launcher.api.result.Outcome
import com.launcher.fake.crypto.InMemoryEncryptedMediaStorage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CleanupMachineryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: CryptoStore
    private lateinit var ledger: SqlDelightBlobReferenceLedger
    private lateinit var clearData: ClearDataDetector
    private var clock: Long = 1_700_000_000_000L

    @Before
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CryptoStore.Schema.create(driver)
        db = CryptoStore(driver)
        ledger = SqlDelightBlobReferenceLedger(db) { clock }
        clearData = ClearDataDetector(db) { clock }
    }

    @After
    fun teardown() {
        driver.close()
    }

    // ─── SqlDelightBlobReferenceLedger ──────────────────────────────────

    @Test
    fun addRef_then_countRefs_returns_one() {
        val uuid = Uuid.random()
        ledger.addRef(uuid, "link-A", "config-current")
        assertEquals(1, ledger.countRefs(uuid, "link-A"))
    }

    @Test
    fun multiple_refs_same_blob_counted_distinctly() {
        val uuid = Uuid.random()
        ledger.addRef(uuid, "link-A", "config-current")
        ledger.addRef(uuid, "link-A", "history-slot-0")
        ledger.addRef(uuid, "link-A", "history-slot-1")
        assertEquals(3, ledger.countRefs(uuid, "link-A"))
    }

    @Test
    fun addRef_same_source_is_idempotent_via_replace() {
        val uuid = Uuid.random()
        ledger.addRef(uuid, "link-A", "config-current")
        ledger.addRef(uuid, "link-A", "config-current")
        assertEquals(1, ledger.countRefs(uuid, "link-A"))
    }

    @Test
    fun removeRef_drops_count() {
        val uuid = Uuid.random()
        ledger.addRef(uuid, "link-A", "config-current")
        ledger.addRef(uuid, "link-A", "history-slot-0")
        ledger.removeRef(uuid, "link-A", "config-current")
        assertEquals(1, ledger.countRefs(uuid, "link-A"))
    }

    @Test
    fun deleteByLink_clears_only_specified_link() {
        val uuid1 = Uuid.random()
        val uuid2 = Uuid.random()
        ledger.addRef(uuid1, "link-A", "config-current")
        ledger.addRef(uuid2, "link-B", "config-current")
        ledger.deleteByLink("link-A")
        assertEquals(0, ledger.countRefs(uuid1, "link-A"))
        assertEquals(1, ledger.countRefs(uuid2, "link-B"))
    }

    @Test
    fun listUuids_per_link_only() {
        val uuid1 = Uuid.random()
        val uuid2 = Uuid.random()
        ledger.addRef(uuid1, "link-A", "config-current")
        ledger.addRef(uuid2, "link-B", "config-current")
        val listA = ledger.listUuids("link-A")
        assertEquals(1, listA.size)
        assertEquals(uuid1, listA.first())
    }

    @Test
    fun removeAllForBlob_drops_every_refSource() {
        val uuid = Uuid.random()
        ledger.addRef(uuid, "link-A", "config-current")
        ledger.addRef(uuid, "link-A", "history-slot-0")
        ledger.addRef(uuid, "link-A", "pending-draft")
        ledger.removeAllForBlob(uuid, "link-A")
        assertEquals(0, ledger.countRefs(uuid, "link-A"))
    }

    // ─── ClearDataDetector ───────────────────────────────────────────────

    @Test
    fun ensureSentinel_writes_on_fresh_db() {
        clock = 1_000_000L
        val ts = clearData.ensureSentinel()
        assertEquals(1_000_000L, ts)
    }

    @Test
    fun ensureSentinel_returns_existing_on_second_call() {
        clock = 1_000_000L
        val first = clearData.ensureSentinel()
        clock = 9_999_999L
        val second = clearData.ensureSentinel()
        assertEquals(first, second)
    }

    @Test
    fun isWithinGracePeriod_true_immediately() {
        clearData.ensureSentinel()
        assertTrue(clearData.isWithinGracePeriod())
    }

    @Test
    fun isWithinGracePeriod_false_after_7_days() {
        clock = 0L
        clearData.ensureSentinel()
        clock = 8L * 24 * 60 * 60 * 1000  // 8 days
        assertFalse(clearData.isWithinGracePeriod())
    }

    @Test
    fun ageMillis_grows_with_clock() {
        clock = 1000L
        clearData.ensureSentinel()
        clock = 1000L + 500L
        assertEquals(500L, clearData.ageMillis())
    }

    // ─── BackgroundReconciler ────────────────────────────────────────────

    @Test
    fun reconcile_skipped_during_grace_period() = runTest {
        clock = 0L
        clearData.ensureSentinel()  // grace period just started
        clock = 1L * 24 * 60 * 60 * 1000  // 1 day later — still within grace
        val storage = InMemoryEncryptedMediaStorage()
        // Upload orphan blob
        val orphan = Uuid.random()
        storage.upload("link-A", orphan, fakeEnvelope())
        val reconciler = BackgroundReconciler(storage, ledger, clearData)
        val result = reconciler.reconcile("link-A")
        assertTrue(result is BackgroundReconciler.ReconcileResult.Skipped)
        // Orphan не удалён — потому что в grace period.
        assertTrue(storage.exists("link-A", orphan))
    }

    @Test
    fun reconcile_deletes_orphan_after_grace() = runTest {
        clock = 0L
        clearData.ensureSentinel()
        clock = 8L * 24 * 60 * 60 * 1000  // past grace
        val storage = InMemoryEncryptedMediaStorage()
        val orphan = Uuid.random()
        storage.upload("link-A", orphan, fakeEnvelope())
        val reconciler = BackgroundReconciler(storage, ledger, clearData)
        val result = reconciler.reconcile("link-A")
        assertTrue(result is BackgroundReconciler.ReconcileResult.Done)
        assertEquals(1, result.orphansDeleted)
        assertFalse(storage.exists("link-A", orphan))
    }

    @Test
    fun reconcile_preserves_blob_with_active_ref() = runTest {
        clock = 0L
        clearData.ensureSentinel()
        clock = 8L * 24 * 60 * 60 * 1000
        val storage = InMemoryEncryptedMediaStorage()
        val live = Uuid.random()
        storage.upload("link-A", live, fakeEnvelope())
        ledger.addRef(live, "link-A", "config-current")
        val reconciler = BackgroundReconciler(storage, ledger, clearData)
        val result = reconciler.reconcile("link-A")
        assertTrue(result is BackgroundReconciler.ReconcileResult.Done)
        assertEquals(0, result.orphansDeleted)
        assertTrue(storage.exists("link-A", live))
    }

    @Test
    fun reconcile_cleans_stale_ledger_entries() = runTest {
        clock = 0L
        clearData.ensureSentinel()
        clock = 8L * 24 * 60 * 60 * 1000
        val storage = InMemoryEncryptedMediaStorage()
        // Ledger references blob that doesn't exist in Storage.
        val stale = Uuid.random()
        ledger.addRef(stale, "link-A", "config-current")
        val reconciler = BackgroundReconciler(storage, ledger, clearData)
        val result = reconciler.reconcile("link-A")
        assertTrue(result is BackgroundReconciler.ReconcileResult.Done)
        assertEquals(1, result.ledgerCleaned)
        assertEquals(0, ledger.countRefs(stale, "link-A"))
    }

    @Test
    fun reconcile_per_link_isolation() = runTest {
        clock = 0L
        clearData.ensureSentinel()
        clock = 8L * 24 * 60 * 60 * 1000
        val storage = InMemoryEncryptedMediaStorage()
        val orphanA = Uuid.random()
        val orphanB = Uuid.random()
        storage.upload("link-A", orphanA, fakeEnvelope())
        storage.upload("link-B", orphanB, fakeEnvelope())
        val reconciler = BackgroundReconciler(storage, ledger, clearData)
        reconciler.reconcile("link-A")
        // link-A orphan удалён, link-B остался.
        assertFalse(storage.exists("link-A", orphanA))
        assertTrue(storage.exists("link-B", orphanB))
    }

    private fun fakeEnvelope(): EncryptedEnvelope = EncryptedEnvelope(
        schemaVersion = com.launcher.api.crypto.SUPPORTED_SCHEMA_VERSION,
        cipherSuiteId = com.launcher.api.crypto.CIPHER_SUITE_ID_V1,
        nonce = ByteArray(com.launcher.api.crypto.XCHACHA20_NONCE_SIZE),
        recipients = listOf(
            com.launcher.api.crypto.Recipient(
                deviceId = com.launcher.api.crypto.DeviceId.random(),
                sealedCEK = ByteArray(com.launcher.api.crypto.SEALED_CEK_SIZE),
            ),
        ),
        ciphertext = byteArrayOf(0x01),
        mac = ByteArray(com.launcher.api.crypto.POLY1305_MAC_SIZE),
    )
}
