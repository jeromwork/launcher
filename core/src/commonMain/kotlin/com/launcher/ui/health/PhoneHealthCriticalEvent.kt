package com.launcher.ui.health

/**
 * Emit-only signal that one of the phone-health indicators transitioned
 * to [PhoneHealthSeverity.Critical] (spec 009 FR-021).
 *
 * Plan §11 C-1: NO EventBus framework — single `MutableSharedFlow` in
 * admin DI scope, single emit site in [HealthToPhoneIndicatorAdapter],
 * **no subscriber in spec 9**. Future subscriber (FCM push admin)
 * lands via TODO-ARCH-012 / SRV-MONITOR-001.
 *
 * TODO(server-roadmap SRV-MONITOR-001): subscriber + FCM push admin
 * when one of these is emitted.
 */
data class PhoneHealthCriticalEvent(
    val indicatorId: String,
    val previousSeverity: PhoneHealthSeverity,
    val newSeverity: PhoneHealthSeverity,
    val detectedAtEpochMillis: Long,
)
