package com.launcher.di

import com.launcher.adapters.setup.GmsAvailabilityAdapter
import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.Surface
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * **realBackend flavor** wiring для spec 010 setup-checks engine (FR-018).
 *
 * Binds:
 *  - [GmsAvailabilityPort] → [GmsAvailabilityAdapter] (real `GoogleApiAvailability`
 *    wrapping, FR-042..FR-044).
 *
 *  - `List<SetupCheck>` — five concrete adapters. **In Phase 1 these are all
 *    stubs that return [CheckStatus.NotConfigured]**; they are progressively
 *    replaced by real adapters in subsequent phases:
 *    - `role_home` → `RoleHomeCheckAdapter` (Phase 2 T037)
 *    - `network_online` → `NetworkOnlineCheckAdapter` (Phase 2 T038)
 *    - `post_notifications` → `PostNotificationsCheckAdapter` (Phase 3 T048)
 *    - `call_phone` → `CallPhoneCheckAdapter` (Phase 4 T060)
 *    - `battery_optimization` → `BatteryOptimizationCheckAdapter` (Phase 5 T074)
 *
 * Plan §11 C-3: NO `SetupCheckRegistry` class — checks are bound directly as
 * `List<SetupCheck>`.
 */
val setupModule: Module = module {

    single<GmsAvailabilityPort> { GmsAvailabilityAdapter(context = androidContext()) }

    single<List<SetupCheck>> {
        listOf(
            stub(id = "role_home", criticality = Criticality.Required),
            stub(id = "post_notifications", criticality = Criticality.Recommended),
            stub(id = "call_phone", criticality = Criticality.Required),
            stub(id = "network_online", criticality = Criticality.Required),
            stub(id = "battery_optimization", criticality = Criticality.Recommended),
        )
    }
}

private fun stub(id: String, criticality: Criticality): SetupCheck =
    object : SetupCheck {
        override val id: String = id
        override val criticality: Criticality = criticality
        override val surfaces: Set<Surface> = setOf(Surface.Settings)
        override suspend fun check(): CheckStatus =
            CheckStatus.NotConfigured(reason = "stub_${id}_pending_phase_implementation")
        override fun resolveIntent(): IntentSpec =
            IntentSpec(category = "stub.$id", action = "open")
    }
