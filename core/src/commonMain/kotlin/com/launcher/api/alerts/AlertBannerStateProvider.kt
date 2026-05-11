package com.launcher.api.alerts

import com.launcher.api.health.HealthRepository
import com.launcher.api.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Pure-domain derivation of the set of currently-visible [AlertBanner]s from:
 *  - [HealthRepository] (`audioStreamMuted` → [AlertBanner.Mute] candidate),
 *  - [SettingsRepository] (banner toggles gate which banners are eligible),
 *  - [airplaneMode] flow (system-level airplane mode → [AlertBanner.Airplane]
 *    candidate). Source is abstracted as `Flow<Boolean>` so `commonMain` stays
 *    free of `Settings.Global` Android types — Android adapter wires the real
 *    `ContentObserver` on its side.
 *
 * Set ordering follows insertion order; rendering layer (`HomeBannerStack`)
 * applies stack semantics from FR-031: Airplane above Mute.
 *
 * Reactivity: emissions debounced by [distinctUntilChanged] — set transitions
 * only emit on actual membership change, not on every upstream tick.
 */
class AlertBannerStateProvider(
    private val healthRepository: HealthRepository,
    private val settingsRepository: SettingsRepository,
    private val airplaneMode: Flow<Boolean>,
) {
    fun observe(): Flow<Set<AlertBanner>> = combine(
        healthRepository.observe(),
        settingsRepository.observe(),
        airplaneMode,
    ) { health, settings, isAirplane ->
        buildSet {
            if (isAirplane && settings.banners.airplane) add(AlertBanner.Airplane)
            if (health.audioStreamMuted && settings.banners.mute) add(AlertBanner.Mute)
        }
    }.distinctUntilChanged()
}
