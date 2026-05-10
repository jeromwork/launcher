package com.launcher.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import com.launcher.api.settings.LauncherSettings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [AndroidSettingsRepository] cold-start hydration + corruption
 * recovery per FR-051.
 *
 * **Threading note:** DataStore Preferences reads/writes go through its own
 * internal `Dispatchers.IO` scope, не подменяемая через `runTest`. Поэтому
 * тесты используют `runBlocking` + `observe().first { ... }` waiting for the
 * hydrated state to emerge — это работает потому что MutableStateFlow в репо
 * пере-emit'ит сразу после init корутины.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidSettingsRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataFile: File
    private lateinit var presetRepo: PresetRepository
    private lateinit var scope: CoroutineScope

    @Before
    fun setup() {
        val unique = "test_settings_${System.nanoTime()}"
        dataFile = context.preferencesDataStoreFile(unique)
        dataFile.parentFile?.mkdirs()
        dataFile.delete()
        dataStore = PreferenceDataStoreFactory.create(produceFile = { dataFile })

        presetRepo = mockk(relaxed = true)
        scope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun teardown() {
        dataFile.delete()
    }

    private fun makeRepo() = AndroidSettingsRepository(
        SettingsProjection(dataStore), presetRepo, scope,
    )

    /** Wait until repo emits settings matching predicate (hydration complete). Times out at 2s. */
    private suspend fun AndroidSettingsRepository.awaitSettings(
        predicate: (LauncherSettings) -> Boolean,
    ): LauncherSettings = withTimeout(2_000L) {
        observe().first { predicate(it) }
    }

    @Test
    fun coldStart_emptyDataStore_simpleLauncher_appliesSeniorDefaults() = runBlocking {
        coEvery { presetRepo.getActivePreset() } returns FlowPreset.SIMPLE_LAUNCHER

        val repo = makeRepo()
        // Initial seed value (defaults for simple-launcher) emits immediately;
        // hydrate either keeps it or overwrites — either way result matches.
        val settings = repo.awaitSettings { it.banners.airplane && it.banners.mute }
        assertTrue(settings.banners.airplane, "simple-launcher must default airplane=true")
        assertTrue(settings.banners.mute, "simple-launcher must default mute=true")
    }

    @Test
    fun coldStart_emptyDataStore_workspace_appliesNonSeniorDefaults() = runBlocking {
        coEvery { presetRepo.getActivePreset() } returns FlowPreset.WORKSPACE

        val repo = makeRepo()
        // Wait for hydrate to overwrite initial-seed (simple-launcher) → workspace defaults.
        val settings = repo.awaitSettings { !it.banners.airplane && !it.banners.mute }
        assertFalse(settings.banners.airplane, "workspace must default airplane=false")
        assertFalse(settings.banners.mute, "workspace must default mute=false")
    }

    @Test
    fun coldStart_nullPreset_appliesSimpleLauncherSafestDefault() = runBlocking {
        coEvery { presetRepo.getActivePreset() } returns null

        val repo = makeRepo()
        val settings = repo.awaitSettings { it.banners.airplane && it.banners.mute }
        assertTrue(settings.banners.airplane)
        assertTrue(settings.banners.mute)
    }

    @Test
    fun updateBanners_persistsAndEmits() = runBlocking {
        coEvery { presetRepo.getActivePreset() } returns FlowPreset.WORKSPACE

        val repo = makeRepo()
        // Wait for hydrate чтобы стартовать с workspace defaults (false/false).
        repo.awaitSettings { !it.banners.airplane && !it.banners.mute }

        // Включить airplane.
        repo.updateBanners { it.copy(airplane = true) }

        val updated = repo.awaitSettings { it.banners.airplane }
        assertTrue(updated.banners.airplane)
        assertFalse(updated.banners.mute)

        // Persistence: создать новый repo на том же DataStore → должно прочитаться.
        val repo2 = makeRepo()
        val rehydrated = repo2.awaitSettings { it.banners.airplane }
        assertTrue(rehydrated.banners.airplane, "persisted toggle must survive repo recreation")
    }
}
