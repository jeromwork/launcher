package com.launcher.di

import com.launcher.adapters.setup.BatteryOptimizationCheckAdapter
import com.launcher.adapters.setup.CallPhoneCheckAdapter
import com.launcher.adapters.setup.GmsAvailabilityAdapter
import com.launcher.adapters.setup.NetworkOnlineCheckAdapter
import com.launcher.adapters.setup.PostNotificationsCheckAdapter
import com.launcher.adapters.setup.RoleHomeCheckAdapter
import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.SetupCheck
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
            // T037 (Phase 2) — real.
            RoleHomeCheckAdapter(context = androidContext()),
            // T038 (Phase 2) — real.
            NetworkOnlineCheckAdapter(context = androidContext()),
            // T048 (Phase 3) — real (skip-on-API<33 internal).
            PostNotificationsCheckAdapter(context = androidContext()),
            // T060 (Phase 4) — real.
            CallPhoneCheckAdapter(context = androidContext()),
            // T074 (Phase 5) — real (with Xiaomi MIUI SecurityException catch).
            BatteryOptimizationCheckAdapter(context = androidContext()),
        )
    }
}

// All five real adapters wired above (T037 / T038 / T048 / T060 / T074). The
// `stub` helper from earlier phases is retired — kept commented for one major
// release как reference for plan §11 C-3 (no Registry; injected via List<>).
