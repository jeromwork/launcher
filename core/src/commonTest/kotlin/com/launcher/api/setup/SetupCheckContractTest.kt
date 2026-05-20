package com.launcher.api.setup

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Spec 010 T028 — contract test base class every real [SetupCheck] adapter
 * MUST inherit from (CLAUDE.md rule 6 — every port has at least a fake test
 * AND a contract test).
 *
 * Adapters under test in subsequent phases:
 *  - Phase 2 T037 — `RoleHomeCheckAdapter`
 *  - Phase 2 T038 — `NetworkOnlineCheckAdapter`
 *  - Phase 3 T048 — `PostNotificationsCheckAdapter`
 *  - Phase 4 T060 — `CallPhoneCheckAdapter`
 *  - Phase 5 T074 — `BatteryOptimizationCheckAdapter`
 *
 * Sub-classes implement [createCheck] and supply a fresh instance. The base
 * class then asserts the universal port contract:
 *  1. [SetupCheck.check] returns non-null exhaustive [CheckStatus] sealed variant.
 *  2. [SetupCheck.check] is idempotent — calling twice in a row yields the
 *     same result (no hidden state mutation between invocations).
 *  3. [SetupCheck.check] does not throw — failure is surfaced via
 *     [CheckStatus.NotConfigured], per FR-020b.
 *  4. [SetupCheck.resolveIntent] returns a non-null [IntentSpec] with both
 *     `category` and `action` non-blank — the platform resolver expects to
 *     translate them into a real `Intent`.
 *  5. [SetupCheck.id] is non-blank and stable across instances of the same class.
 */
abstract class SetupCheckContractTest {

    /** Sub-class provides a fresh, isolated adapter instance for each test. */
    protected abstract fun createCheck(): SetupCheck

    @Test
    fun check_returns_non_null_sealed_variant() = runTest {
        val status: CheckStatus = createCheck().check()
        when (status) {
            CheckStatus.Ok -> Unit
            is CheckStatus.NotConfigured -> assertNotNull(status.reason)
        }
    }

    @Test
    fun check_is_idempotent_across_two_invocations() = runTest {
        val sut = createCheck()
        val first = sut.check()
        val second = sut.check()
        assertEquals(first::class, second::class, "check() must be idempotent")
    }

    @Test
    fun check_does_not_throw() = runTest {
        // The contract is: SetupCheck implementations surface failures as
        // CheckStatus.NotConfigured rather than throwing. Genuine system
        // exceptions are caught by the engine wrapper (T075) — but at the
        // adapter level we still prefer the explicit-NotConfigured path.
        val sut = createCheck()
        // Must not throw.
        sut.check()
    }

    @Test
    fun resolveIntent_returns_non_blank_category_and_action() {
        val spec = createCheck().resolveIntent()
        assertTrue(spec.category.isNotBlank(), "IntentSpec.category must not be blank")
        assertTrue(spec.action.isNotBlank(), "IntentSpec.action must not be blank")
    }

    @Test
    fun id_is_non_blank() {
        assertTrue(createCheck().id.isNotBlank(), "SetupCheck.id must not be blank")
    }

    @Test
    fun id_is_stable_across_instances() {
        val a = createCheck()
        val b = createCheck()
        assertEquals(a.id, b.id, "SetupCheck.id must be stable across instances")
    }
}
