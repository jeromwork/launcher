package com.launcher.api.setup

/**
 * Domain port for a single piece of launcher configuration that may or may not
 * be in place (spec 010 FR-017).
 *
 * Five real adapters live in androidMain (`RoleHomeCheckAdapter`,
 * `PostNotificationsCheckAdapter`, `CallPhoneCheckAdapter`,
 * `NetworkOnlineCheckAdapter`, `BatteryOptimizationCheckAdapter`) and are
 * wired as `List<SetupCheck>` via Koin (plan §11 C-3 — no registry class).
 *
 * Each implementation MUST be:
 *  - **idempotent**: calling [check] N times in a row never changes external state.
 *  - **side-effect-free** during [check]: only the [resolveIntent] navigation
 *    triggers a state-changing system call (request role, open settings, etc).
 *  - **non-throwing for normal failures**: surface them as [CheckStatus.NotConfigured].
 *    Genuine exceptions (e.g. Xiaomi `SecurityException`) are caught by the
 *    engine in spec 010 T075 and turned into `NotConfigured(reason)` per FR-020b.
 */
interface SetupCheck {
    /** Stable identifier (e.g. `"role_home"`, `"call_phone"`); used in diagnostic events and tests. */
    val id: String

    /** Whether failing this check blocks the launcher's core function (FR-019). */
    val criticality: Criticality

    /** Where the check's status is consumed (FR-017, FR-020a). */
    val surfaces: Set<Surface>

    /** Suspend-friendly status read. Implementations stay sub-50 ms on warm devices. */
    suspend fun check(): CheckStatus

    /**
     * Description of the Intent the launcher should fire when the user taps
     * «Настроить» on the «What needs configuring» screen (FR-020). The androidMain
     * `SetupIntentResolver` translates this primitive spec into a real
     * `android.content.Intent`.
     */
    fun resolveIntent(): IntentSpec
}
