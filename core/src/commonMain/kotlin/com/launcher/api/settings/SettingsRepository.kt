package com.launcher.api.settings

import kotlinx.coroutines.flow.Flow

/**
 * Port (interface) exposing user-toggleable launcher settings.
 *
 * Implementations:
 *  - real (`AndroidSettingsRepository`, androidMain): backed by Preferences
 *    DataStore (key `com.launcher.settings.banners_v1`); on corruption falls
 *    back to [LauncherSettings.defaultsForPreset] (FR-051).
 *  - fake (`FakeSettingsRepository`, commonTest): in-memory `MutableStateFlow`
 *    for tests (FR-048).
 */
interface SettingsRepository {
    /** Hot flow of current settings. Replays last value on subscribe. */
    fun observe(): Flow<LauncherSettings>

    /** Synchronous read of last-known settings. */
    fun snapshot(): LauncherSettings

    /**
     * Atomically transform banner toggles. Coroutine suspends until persistence
     * write completes (so callers can `.await()` and observe the next emit).
     */
    suspend fun updateBanners(transform: (BannerToggles) -> BannerToggles)
}
