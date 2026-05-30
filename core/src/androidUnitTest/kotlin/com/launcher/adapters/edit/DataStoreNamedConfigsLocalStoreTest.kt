package com.launcher.adapters.edit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.edit.NamedConfig
import com.launcher.api.edit.StoreError
import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Robolectric tests for [DataStoreNamedConfigsLocalStore] (T055).
 *
 * Covers:
 *  - Roundtrip: empty store starts empty; create → snapshot → restart →
 *    same configs (T058 process-death simulation).
 *  - All [NamedConfigsLocalStore] contract invariants per
 *    [com.launcher.api.edit.FakeNamedConfigsLocalStoreContractTest].
 *  - Schema-version forward-compat fail-closed (T041 wire-format).
 *
 * **Threading note**: same as AndroidSettingsRepositoryTest — DataStore I/O
 * uses internal Dispatchers.IO, so tests use `runBlocking` + `first { }`.
 */
@RunWith(RobolectricTestRunner::class)
class DataStoreNamedConfigsLocalStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataFile: File

    private val device1 = "d1111111-1111-4111-8111-111111111111"
    private val device2 = "d2222222-2222-4222-8222-222222222222"

    private fun config(
        name: String,
        isDefault: Boolean = false,
        devices: Set<String> = setOf(device1),
    ): NamedConfig = NamedConfig(
        configName = name,
        isDefault = isDefault,
        presetId = "workspace",
        deviceClass = "phone",
        activeDeviceIds = devices,
    )

    @Before
    fun setup() {
        val unique = "test_named_configs_${System.nanoTime()}"
        dataFile = context.preferencesDataStoreFile(unique)
        dataFile.parentFile?.mkdirs()
        dataFile.delete()
        dataStore = PreferenceDataStoreFactory.create(produceFile = { dataFile })
    }

    @After
    fun teardown() {
        dataFile.delete()
    }

    private fun makeStore() = DataStoreNamedConfigsLocalStore(dataStore)

    // ─── Empty store / cold start ────────────────────────────────────────

    @Test
    fun empty_store_emits_empty_list() = runBlocking {
        val store = makeStore()
        val first = store.configs.first()
        assertEquals(emptyList<NamedConfig>(), first)
    }

    // ─── Roundtrip + persistence across re-instantiation ─────────────────

    @Test
    fun create_then_read_returns_same_config() = runBlocking {
        val store = makeStore()
        val r = store.create(config("default", isDefault = true))
        assertTrue(r is Outcome.Success)

        val snapshot = store.configs.first()
        assertEquals(1, snapshot.size)
        assertEquals("default", snapshot[0].configName)
        assertTrue(snapshot[0].isDefault)
    }

    @Test
    fun T058_persists_data_for_subsequent_store_instances() = runBlocking {
        // Write через store A using the shared DataStore instance.
        val storeA = makeStore()
        storeA.create(config("default", isDefault = true))
        storeA.create(config("job", isDefault = false))

        // Process-death simulation full restart-with-new-DataStore is not
        // possible from a unit test (PreferenceDataStoreFactory single-process
        // contract). Instead verify another adapter instance over the SAME
        // DataStore reads what storeA wrote — covers the data-flow we care
        // about (multiple adapter instances ↔ shared storage).
        val storeB = DataStoreNamedConfigsLocalStore(dataStore)

        val snapshot = storeB.configs.first()
        assertEquals(2, snapshot.size)
        assertEquals(setOf("default", "job"), snapshot.map { it.configName }.toSet())
    }

    // ─── 5-config limit (FR-003c) ─────────────────────────────────────────

    @Test
    fun create_returns_LimitReached_at_sixth_config() = runBlocking {
        val store = makeStore()
        for (i in 1..5) {
            store.create(config("cfg$i", isDefault = i == 1))
        }
        val r = store.create(config("cfg6"))
        assertEquals(Outcome.Failure(StoreError.LimitReached), r)
    }

    // ─── Single default invariant (FR-003a) ───────────────────────────────

    @Test
    fun creating_default_clears_other_defaults() = runBlocking {
        val store = makeStore()
        store.create(config("first", isDefault = true))
        store.create(config("second", isDefault = true))

        val defaults = store.configs.first().filter { it.isDefault }
        assertEquals(1, defaults.size)
        assertEquals("second", defaults[0].configName)
    }

    @Test
    fun markDefault_flips_target_and_clears_others() = runBlocking {
        val store = makeStore()
        store.create(config("first", isDefault = true))
        store.create(config("second", isDefault = false))

        val r = store.markDefault("second")
        assertTrue(r is Outcome.Success)

        val snap = store.configs.first()
        assertEquals(false, snap.first { it.configName == "first" }.isDefault)
        assertEquals(true, snap.first { it.configName == "second" }.isDefault)
    }

    @Test
    fun update_clearing_only_default_returns_DefaultMustExist() = runBlocking {
        val store = makeStore()
        store.create(config("only", isDefault = true))

        val r = store.update("only") { it.copy(isDefault = false) }
        assertEquals(Outcome.Failure(StoreError.DefaultMustExist), r)
    }

    // ─── Case-insensitive name uniqueness ─────────────────────────────────

    @Test
    fun create_returns_NameAlreadyExists_for_duplicate() = runBlocking {
        val store = makeStore()
        store.create(config("home", isDefault = true))

        val r = store.create(config("home"))
        assertEquals(Outcome.Failure(StoreError.NameAlreadyExists("home")), r)
    }

    @Test
    fun create_returns_NameAlreadyExists_for_case_insensitive_duplicate() = runBlocking {
        val store = makeStore()
        store.create(config("Home", isDefault = true))

        val r = store.create(config("HOME"))
        assertEquals(Outcome.Failure(StoreError.NameAlreadyExists("HOME")), r)
    }

    // ─── Lifecycle (FR-003) ───────────────────────────────────────────────

    @Test
    fun applyToCurrentDevice_adds_device_clears_orphanedAt() = runBlocking {
        val store = makeStore()
        // Bootstrap config with empty devices (simulate orphan).
        store.create(
            NamedConfig(
                configName = "orphan",
                isDefault = true,
                presetId = "workspace",
                deviceClass = "phone",
                activeDeviceIds = emptySet(),
                orphanedAt = 1_700_000_000_000L,
            ),
        )

        val r = store.applyToCurrentDevice("orphan", device2)
        assertTrue(r is Outcome.Success)

        val updated = store.configs.first().first { it.configName == "orphan" }
        assertEquals(setOf(device2), updated.activeDeviceIds)
        assertNull(updated.orphanedAt)
    }

    @Test
    fun removeFromCurrentDevice_sets_orphanedAt_when_last_device() = runBlocking {
        val store = makeStore()
        store.create(config("active", isDefault = false, devices = setOf(device1)))
        store.create(config("default-backup", isDefault = true, devices = setOf(device2)))

        val r = store.removeFromCurrentDevice("active", device1, nowMillis = 1_800_000_000_000L)
        assertTrue("expected Success, got: $r", r is Outcome.Success)

        val updated = store.configs.first().first { it.configName == "active" }
        assertEquals(emptySet<String>(), updated.activeDeviceIds)
        assertEquals(1_800_000_000_000L, updated.orphanedAt)
    }

    @Test
    fun removeFromCurrentDevice_refuses_if_would_leave_no_default() = runBlocking {
        val store = makeStore()
        store.create(config("only", isDefault = true, devices = setOf(device1)))

        val r = store.removeFromCurrentDevice("only", device1, nowMillis = 1L)
        assertEquals(Outcome.Failure(StoreError.DefaultMustExist), r)
    }

    // ─── Forward-compat fail-closed (T041 wire-format) ────────────────────

    @Test
    fun read_with_future_schemaVersion_emits_empty_list_gracefully() = runBlocking {
        // Inject a v99 JSON directly into DataStore — store should refuse to
        // parse and emit empty (graceful degrade, не crash).
        val futureJson = """{"schemaVersion": 99, "configs": []}"""
        dataStore.edit { prefs ->
            prefs[DataStoreNamedConfigsLocalStore.KEY] = futureJson
        }
        val store = makeStore()

        val snapshot = store.configs.first()
        assertEquals(emptyList<NamedConfig>(), snapshot)
    }
}
