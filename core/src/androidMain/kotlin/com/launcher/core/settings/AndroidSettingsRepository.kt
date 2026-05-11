package com.launcher.core.settings

import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import com.launcher.api.settings.BannerToggles
import com.launcher.api.settings.LauncherSettings
import com.launcher.api.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real Android implementation of [SettingsRepository] per FR-033, FR-051.
 *
 * Cold-start path:
 *  1. Read DataStore via [SettingsProjection].
 *  2. If file exists и парсится — seed StateFlow с persisted value.
 *  3. If file отсутствует ИЛИ corrupted — apply
 *     [LauncherSettings.defaultsForPreset] using current preset slug
 *     (FR-051), persist defaults в одну recovery write, seed StateFlow.
 *
 * Если preset не выбран yet (first launch до preset picker) — defaults
 * применяются с `simple-launcher` slug as safest baseline (senior protection
 * enabled by default — лучше иметь лишние банеры чем не иметь нужных).
 *
 * Mutex serialises [updateBanners] вызовы — DataStore сам safe для
 * concurrent edits, но нам надо чтобы StateFlow update + persist happened
 * atomically относительно других updates.
 */
class AndroidSettingsRepository(
    private val projection: SettingsProjection,
    private val presetRepository: PresetRepository,
    scope: CoroutineScope,
    /**
     * Dispatcher для cold-start hydration. Defaults to [Dispatchers.IO] для
     * production; tests inject TestDispatcher для детерминированного поведения.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsRepository {

    private val state = MutableStateFlow(LauncherSettings.defaultsForPreset(DEFAULT_PRESET_SLUG))
    private val updateMutex = Mutex()

    init {
        // Cold-start seed (synchronous-ish: blocks first observe() emission
        // until hydration completes; for Compose UI этого не страшно потому
        // что HomeScreen subscribes after Application.onCreate finishes).
        scope.launch(ioDispatcher) {
            hydrateFromProjection()
        }
    }

    private suspend fun hydrateFromProjection() {
        val persisted = projection.flow.firstOrNull()
        if (persisted != null) {
            state.value = persisted
            return
        }
        // No persisted file — apply preset-aware defaults и persist once.
        val presetSlug = currentPresetSlug()
        val defaults = LauncherSettings.defaultsForPreset(presetSlug)
        state.value = defaults
        try {
            projection.write(defaults)
        } catch (_: Throwable) {
            // Persist failure не критично — defaults в памяти; следующий update
            // снова попытается записать.
        }
    }

    private suspend fun currentPresetSlug(): String =
        presetRepository.getActivePreset()?.slug ?: DEFAULT_PRESET_SLUG

    override fun observe(): Flow<LauncherSettings> = state.asStateFlow()
    override fun snapshot(): LauncherSettings = state.value

    override suspend fun updateBanners(transform: (BannerToggles) -> BannerToggles) {
        updateMutex.withLock {
            val current = state.value
            val updated = current.copy(banners = transform(current.banners))
            state.value = updated
            projection.write(updated)
        }
    }

    companion object {
        /** Safest default until user picks a preset (senior protection on). */
        private val DEFAULT_PRESET_SLUG = FlowPreset.SIMPLE_LAUNCHER.slug
    }
}
