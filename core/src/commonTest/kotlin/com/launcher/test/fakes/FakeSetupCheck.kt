package com.launcher.test.fakes

import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.Surface

/**
 * In-memory [SetupCheck] used by spec 010 tests (CLAUDE.md rule 6 — mock-first).
 *
 * - [status] is mutable so tests can transition Ok ↔ NotConfigured between calls.
 * - [resolveIntentSpec] is captured at construction; production code never
 *   inspects its contents (it's handed to the platform adapter), so tests
 *   need only check the [SetupCheck.resolveIntent] return identity.
 *
 * Used by:
 *  - Koin `setupModule` (mockBackend) — provides a `List<SetupCheck>` of 5 instances.
 *  - Settings UI tests (`SetupChecksBadgeTest`, `WhatNeedsConfiguringScreenTest`).
 *  - Exception-handling test (`SetupCheckExceptionHandlingTest`) — see [FakeThrowingSetupCheck].
 */
class FakeSetupCheck(
    override val id: String,
    override val criticality: Criticality = Criticality.Required,
    override val surfaces: Set<Surface> = setOf(Surface.Settings),
    private val resolveIntentSpec: IntentSpec = IntentSpec(
        category = "fake.${id}", action = "open",
    ),
    initialStatus: CheckStatus = CheckStatus.Ok,
) : SetupCheck {

    var status: CheckStatus = initialStatus

    override suspend fun check(): CheckStatus = status

    override fun resolveIntent(): IntentSpec = resolveIntentSpec
}

/**
 * Variant that throws on [check] — used to verify the spec 010 FR-020b engine
 * wraps each invocation in try-catch and converts the throw into
 * [CheckStatus.NotConfigured].
 */
class FakeThrowingSetupCheck(
    override val id: String,
    private val exception: Throwable = SecurityException("simulated Xiaomi MIUI block"),
    override val criticality: Criticality = Criticality.Required,
    override val surfaces: Set<Surface> = setOf(Surface.Settings),
) : SetupCheck {

    override suspend fun check(): CheckStatus = throw exception

    override fun resolveIntent(): IntentSpec =
        IntentSpec(category = "fake.throwing", action = "open")
}
