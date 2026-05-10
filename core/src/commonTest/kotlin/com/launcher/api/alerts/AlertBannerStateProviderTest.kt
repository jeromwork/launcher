package com.launcher.api.alerts

import com.launcher.api.settings.BannerToggles
import com.launcher.api.settings.LauncherSettings
import com.launcher.test.fakes.FakeHealthRepository
import com.launcher.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [AlertBannerStateProvider] derivation logic per FR-026, FR-027, FR-031.
 *
 * Verifies the combine() rule: banner appears when (state predicate) AND (toggle on).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertBannerStateProviderTest {

    private fun provider(
        health: FakeHealthRepository = FakeHealthRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        airplaneMode: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ) = AlertBannerStateProvider(
        healthRepository = health,
        settingsRepository = settings,
        airplaneMode = airplaneMode,
    )

    @Test
    fun emptySet_whenNoConditionsTriggered() = runTest {
        val result = provider().observe().first()
        assertTrue(result.isEmpty(), "expected empty banner set, got $result")
    }

    @Test
    fun airplaneBanner_whenAirplaneOn_andToggleOn() = runTest {
        val result = provider(
            settings = FakeSettingsRepository(LauncherSettings(banners = BannerToggles(airplane = true))),
            airplaneMode = MutableStateFlow(true),
        ).observe().first()
        assertEquals(setOf(AlertBanner.Airplane), result)
    }

    @Test
    fun noAirplaneBanner_whenAirplaneOn_butToggleOff() = runTest {
        val result = provider(
            settings = FakeSettingsRepository(LauncherSettings(banners = BannerToggles(airplane = false))),
            airplaneMode = MutableStateFlow(true),
        ).observe().first()
        assertTrue(result.isEmpty(), "toggle off must suppress banner")
    }

    @Test
    fun muteBanner_whenAudioMuted_andToggleOn() = runTest {
        val result = provider(
            health = FakeHealthRepository(FakeHealthRepository.defaultHealth.copy(audioStreamMuted = true, ringerVolumePercent = 0)),
            settings = FakeSettingsRepository(LauncherSettings(banners = BannerToggles(mute = true))),
        ).observe().first()
        assertEquals(setOf(AlertBanner.Mute), result)
    }

    @Test
    fun noMuteBanner_whenMuted_butToggleOff() = runTest {
        val result = provider(
            health = FakeHealthRepository(FakeHealthRepository.defaultHealth.copy(audioStreamMuted = true)),
            settings = FakeSettingsRepository(LauncherSettings(banners = BannerToggles(mute = false))),
        ).observe().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun bothBanners_whenBothConditionsAndTogglesOn() = runTest {
        val result = provider(
            health = FakeHealthRepository(FakeHealthRepository.defaultHealth.copy(audioStreamMuted = true)),
            settings = FakeSettingsRepository(LauncherSettings(banners = BannerToggles(airplane = true, mute = true))),
            airplaneMode = MutableStateFlow(true),
        ).observe().first()
        assertEquals(setOf(AlertBanner.Airplane, AlertBanner.Mute), result)
    }

    @Test
    fun stackOrder_airplaneBeforeMute() = runTest {
        // Set iteration order = insertion order. AlertBannerStateProvider добавляет
        // Airplane сначала, потом Mute (FR-031: «Airplane above Mute»).
        val result = provider(
            health = FakeHealthRepository(FakeHealthRepository.defaultHealth.copy(audioStreamMuted = true)),
            settings = FakeSettingsRepository(LauncherSettings(banners = BannerToggles(airplane = true, mute = true))),
            airplaneMode = MutableStateFlow(true),
        ).observe().first()
        val list = result.toList()
        assertEquals(AlertBanner.Airplane, list[0], "Airplane must come first")
        assertEquals(AlertBanner.Mute, list[1], "Mute must come second")
    }
}
