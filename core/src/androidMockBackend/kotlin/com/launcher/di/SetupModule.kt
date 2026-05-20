package com.launcher.di

import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.Surface
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * **mockBackend flavor** wiring для spec 010 setup-checks engine (FR-018).
 *
 * Binds:
 *  - `List<SetupCheck>` — 5 in-memory checks with stable ids matching the
 *    five real adapters (`role_home`, `post_notifications`, `call_phone`,
 *    `network_online`, `battery_optimization`). All default to
 *    [CheckStatus.NotConfigured] so the Settings `!N` / `?M` badge actually
 *    surfaces something in mockBackend builds for QA. Tests scope their own
 *    `MockSetupCheck` list via `koinApplication { … }` per CLAUDE.md rule 6.
 *
 *  - [GmsAvailabilityPort] — mock returns [GmsStatus.Available] so the
 *    wizard proceeds normally; the hard-block path is exercised via the
 *    dedicated test fake (`FakeGmsAvailabilityPort`).
 *
 * Plan §11 C-3: NO `SetupCheckRegistry` class — checks are bound directly as
 * `List<SetupCheck>` (enforced by code review + the absence of a Registry
 * type in [api.setup]).
 */
val setupModule: Module = module {

    single<GmsAvailabilityPort> {
        object : GmsAvailabilityPort {
            override suspend fun status(): GmsStatus = GmsStatus.Available
        }
    }

    single<List<SetupCheck>> {
        listOf(
            mockCheck(id = "role_home", criticality = Criticality.Required),
            mockCheck(id = "post_notifications", criticality = Criticality.Recommended),
            mockCheck(id = "call_phone", criticality = Criticality.Required),
            mockCheck(id = "network_online", criticality = Criticality.Required),
            mockCheck(id = "battery_optimization", criticality = Criticality.Recommended),
        )
    }
}

private fun mockCheck(id: String, criticality: Criticality): SetupCheck =
    object : SetupCheck {
        override val id: String = id
        override val criticality: Criticality = criticality
        override val surfaces: Set<Surface> = setOf(Surface.Settings)
        override suspend fun check(): CheckStatus =
            CheckStatus.NotConfigured(reason = "mock_${id}_not_configured")
        override fun resolveIntent(): IntentSpec =
            IntentSpec(category = "mock.$id", action = "open")
    }
