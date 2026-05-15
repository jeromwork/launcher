package com.launcher.ui.health

import com.launcher.api.apps.IconRef
import com.launcher.api.health.Connectivity
import com.launcher.api.health.Health
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Pure-Kotlin mapper from spec-006 [Health] wire format to four
 * UI-facing [PhoneHealthIndicator]s — battery, connectivity, audio,
 * lastSeen (spec 009 FR-017, FR-018, FR-019, FR-020 client-side severity
 * computation).
 *
 * Side-channel: emits [PhoneHealthCriticalEvent] when an indicator
 * transitions to [PhoneHealthSeverity.Critical] (FR-021). Plan §11 C-1:
 * single `MutableSharedFlow` emit site; no subscriber в спеке 9.
 *
 * Per-indicator previous-severity is tracked internally so the flow
 * fires only on transitions, not every emission.
 */
class HealthToPhoneIndicatorAdapter(
    private val preset: PhoneHealthPreset = DEFAULT_PHONE_HEALTH_PRESET,
    private val clock: () -> Long,
) {
    private val previousSeverityById = mutableMapOf<String, PhoneHealthSeverity>()

    /**
     * Event emitter — collected (in spec ≥ 10) by FCM push admin job
     * (SRV-MONITOR-001 future server-side route).
     */
    val criticalEvents: MutableSharedFlow<PhoneHealthCriticalEvent> =
        MutableSharedFlow(extraBufferCapacity = 4)

    fun map(health: Health): List<PhoneHealthIndicator> {
        val now = clock()
        val battery = batteryIndicator(health)
        val connectivity = connectivityIndicator(health)
        val audio = audioIndicator(health)
        val lastSeen = lastSeenIndicator(health, now)
        return listOf(battery, connectivity, audio, lastSeen)
            .onEach { emitIfCriticalTransition(it) }
    }

    private fun batteryIndicator(h: Health): PhoneHealthIndicator {
        val severity = when {
            h.batteryPercent <= preset.battery.criticalBelowPercent -> PhoneHealthSeverity.Critical
            h.batteryPercent <= preset.battery.warningBelowPercent -> PhoneHealthSeverity.Warning
            else -> PhoneHealthSeverity.Info
        }
        val value = "${h.batteryPercent} %"
        return PhoneHealthIndicator(
            id = ID_BATTERY,
            label = LABEL_BATTERY,
            value = value,
            severity = severity,
            iconRes = ICON_BATTERY,
            contentDescription = "Заряд: $value, ${severityRu(severity)}",
            updatedAt = clock(),
        )
    }

    private fun connectivityIndicator(h: Health): PhoneHealthIndicator {
        val severity = if (h.connectivity == Connectivity.None) preset.connectivityNoneSeverity else PhoneHealthSeverity.Info
        val value = when (h.connectivity) {
            Connectivity.Wifi -> "Wi-Fi"
            Connectivity.Mobile -> "Мобильная сеть"
            Connectivity.None -> "Нет связи"
        }
        return PhoneHealthIndicator(
            id = ID_CONNECTIVITY,
            label = LABEL_CONNECTIVITY,
            value = value,
            severity = severity,
            iconRes = ICON_CONNECTIVITY,
            contentDescription = "Сеть: $value, ${severityRu(severity)}",
            updatedAt = clock(),
        )
    }

    private fun audioIndicator(h: Health): PhoneHealthIndicator {
        val severity = if (h.audioStreamMuted) preset.audioMutedSeverity else PhoneHealthSeverity.Info
        val value = if (h.audioStreamMuted) "Без звука" else "${h.ringerVolumePercent} %"
        return PhoneHealthIndicator(
            id = ID_AUDIO,
            label = LABEL_AUDIO,
            value = value,
            severity = severity,
            iconRes = ICON_AUDIO,
            contentDescription = "Звук: $value, ${severityRu(severity)}",
            updatedAt = clock(),
        )
    }

    private fun lastSeenIndicator(h: Health, now: Long): PhoneHealthIndicator {
        val deltaMillis = (now - h.lastSeen).coerceAtLeast(0L)
        val deltaHours = deltaMillis / MILLIS_PER_HOUR
        val severity = when {
            deltaHours >= preset.lastSeen.criticalAfterHours -> PhoneHealthSeverity.Critical
            deltaHours >= preset.lastSeen.warningAfterHours -> PhoneHealthSeverity.Warning
            else -> PhoneHealthSeverity.Info
        }
        val value = when {
            deltaHours < 1 -> "Только что"
            deltaHours < 24 -> "$deltaHours ч назад"
            else -> "${deltaHours / 24} дн назад"
        }
        return PhoneHealthIndicator(
            id = ID_LAST_SEEN,
            label = LABEL_LAST_SEEN,
            value = value,
            severity = severity,
            iconRes = ICON_LAST_SEEN,
            contentDescription = "На связи: $value, ${severityRu(severity)}",
            updatedAt = clock(),
        )
    }

    private fun emitIfCriticalTransition(indicator: PhoneHealthIndicator) {
        val previous = previousSeverityById[indicator.id]
        previousSeverityById[indicator.id] = indicator.severity
        if (previous != null && previous != indicator.severity &&
            indicator.severity == PhoneHealthSeverity.Critical
        ) {
            criticalEvents.tryEmit(
                PhoneHealthCriticalEvent(
                    indicatorId = indicator.id,
                    previousSeverity = previous,
                    newSeverity = indicator.severity,
                    detectedAtEpochMillis = indicator.updatedAt,
                ),
            )
        }
    }

    companion object {
        const val ID_BATTERY = "battery"
        const val ID_CONNECTIVITY = "connectivity"
        const val ID_AUDIO = "audio"
        const val ID_LAST_SEEN = "lastSeen"

        private const val LABEL_BATTERY = "Заряд"
        private const val LABEL_CONNECTIVITY = "Сеть"
        private const val LABEL_AUDIO = "Звук"
        private const val LABEL_LAST_SEEN = "На связи"

        // Icon refs resolved by `androidMain` UI to real drawables; resourceId 0
        // means "fallback icon" — the indicator-screen renderer maps id → vector.
        private val ICON_BATTERY = IconRef(packageName = "self", resourceId = 0)
        private val ICON_CONNECTIVITY = IconRef(packageName = "self", resourceId = 0)
        private val ICON_AUDIO = IconRef(packageName = "self", resourceId = 0)
        private val ICON_LAST_SEEN = IconRef(packageName = "self", resourceId = 0)

        private const val MILLIS_PER_HOUR = 60L * 60L * 1000L

        private fun severityRu(s: PhoneHealthSeverity): String = when (s) {
            PhoneHealthSeverity.Info -> "норма"
            PhoneHealthSeverity.Warning -> "предупреждение"
            PhoneHealthSeverity.Critical -> "критично"
        }
    }
}
