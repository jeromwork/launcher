package com.launcher.test.fakes

import com.launcher.api.health.Connectivity
import com.launcher.api.health.Health
import com.launcher.api.health.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake of [HealthRepository] для тестов (FR-048).
 *
 * Default initial state — "healthy device": WiFi, 80% battery, ringer 50%, not muted.
 * Tests override через [setHealth] для конкретного сценария.
 */
class FakeHealthRepository(
    initial: Health = defaultHealth,
) : HealthRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<Health> = state.asStateFlow()
    override fun snapshot(): Health = state.value

    /** Test helper: переключить health snapshot. */
    fun setHealth(health: Health) {
        state.value = health
    }

    companion object {
        val defaultHealth = Health(
            batteryPercent = 80,
            charging = false,
            connectivity = Connectivity.Wifi,
            ringerVolumePercent = 50,
            audioStreamMuted = false,
            lastSeen = 0L,
            appVersion = "test",
        )
    }
}
