package com.launcher.test.fakes

import com.launcher.api.settings.BannerToggles
import com.launcher.api.settings.LauncherSettings
import com.launcher.api.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake of [SettingsRepository] для тестов (FR-048).
 *
 * Default initial — все banner toggles OFF (matches non-senior preset defaults
 * per [LauncherSettings.defaultsForPreset]).
 */
class FakeSettingsRepository(
    initial: LauncherSettings = LauncherSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<LauncherSettings> = state.asStateFlow()
    override fun snapshot(): LauncherSettings = state.value

    override suspend fun updateBanners(transform: (BannerToggles) -> BannerToggles) {
        val current = state.value
        state.value = current.copy(banners = transform(current.banners))
    }

    /** Test helper: replace the entire settings snapshot. */
    fun setSettings(settings: LauncherSettings) {
        state.value = settings
    }
}
