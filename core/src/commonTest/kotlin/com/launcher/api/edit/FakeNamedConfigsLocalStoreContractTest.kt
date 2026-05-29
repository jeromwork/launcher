package com.launcher.api.edit

import com.launcher.api.result.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contract tests for [NamedConfigsLocalStore] invariants per
 * [contracts/named-config-local.md](../../../../specs/014-tile-editing-admin-senior-profiles/contracts/named-config-local.md)
 * §Invariants + FR-003, FR-003a, FR-003c.
 *
 * These tests target [FakeNamedConfigsLocalStore] (T051) — the same contract
 * applies to [DataStoreNamedConfigsLocalStore] (T056) which uses identical
 * test names in androidTest source set.
 *
 * Invariants covered:
 *  1. size 0..5 (LimitReached).
 *  2. exactly one isDefault==true (FR-003a, DefaultMustExist).
 *  3. case-insensitive name uniqueness (NameAlreadyExists).
 *  4. activeDeviceIds + orphanedAt lifecycle (FR-003).
 *
 * Trace: spec 014 T050.
 */
class FakeNamedConfigsLocalStoreContractTest {

    private val device1 = "d1111111-1111-4111-8111-111111111111"
    private val device2 = "d2222222-2222-4222-8222-222222222222"

    private fun config(
        name: String,
        isDefault: Boolean = false,
        presetId: String = "workspace",
        devices: Set<String> = setOf(device1),
        orphanedAt: Long? = null,
    ): NamedConfig = NamedConfig(
        configName = name,
        isDefault = isDefault,
        presetId = presetId,
        deviceClass = "phone",
        activeDeviceIds = devices,
        orphanedAt = orphanedAt,
    )

    // ─── Invariant 1: 5-config limit ──────────────────────────────────────

    @Test
    fun create_succeeds_up_to_five_configs() = runTest {
        val store = FakeNamedConfigsLocalStore()
        for (i in 1..5) {
            val r = store.create(config(name = "cfg$i", isDefault = i == 1))
            assertTrue(r is Outcome.Success, "create cfg$i should succeed: $r")
        }
        assertEquals(5, store.snapshot().size)
    }

    @Test
    fun create_returns_LimitReached_at_sixth_config() = runTest {
        val store = FakeNamedConfigsLocalStore()
        for (i in 1..5) {
            store.create(config(name = "cfg$i", isDefault = i == 1))
        }
        val r = store.create(config(name = "cfg6"))
        assertEquals(Outcome.Failure(StoreError.LimitReached), r)
    }

    // ─── Invariant 2: single default ──────────────────────────────────────

    @Test
    fun creating_default_clears_other_defaults_atomically() = runTest {
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(config("first", isDefault = true)),
        )
        val r = store.create(config("second", isDefault = true))
        assertTrue(r is Outcome.Success)

        val defaults = store.snapshot().filter { it.isDefault }
        assertEquals(1, defaults.size, "exactly one default expected: $defaults")
        assertEquals("second", defaults[0].configName)
    }

    @Test
    fun markDefault_flips_target_and_clears_others() = runTest {
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(
                config("first", isDefault = true),
                config("second", isDefault = false),
                config("third", isDefault = false),
            ),
        )
        val r = store.markDefault("second")
        assertTrue(r is Outcome.Success)

        val snap = store.snapshot()
        assertEquals(false, snap.first { it.configName == "first" }.isDefault)
        assertEquals(true, snap.first { it.configName == "second" }.isDefault)
        assertEquals(false, snap.first { it.configName == "third" }.isDefault)
    }

    @Test
    fun markDefault_returns_NotFound_for_unknown_name() = runTest {
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(config("only", isDefault = true)),
        )
        val r = store.markDefault("missing")
        assertEquals(Outcome.Failure(StoreError.NotFound), r)
    }

    @Test
    fun update_clearing_only_default_returns_DefaultMustExist() = runTest {
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(config("only", isDefault = true)),
        )
        val r = store.update("only") { it.copy(isDefault = false) }
        assertEquals(Outcome.Failure(StoreError.DefaultMustExist), r)
    }

    @Test
    fun update_clearing_default_when_other_exists_is_allowed() = runTest {
        // Edge case: somehow store has two defaults (e.g. legacy bug recovery).
        // Update should allow clearing one when the other still exists.
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(
                config("a", isDefault = true),
                config("b", isDefault = true),
            ),
        )
        val r = store.update("a") { it.copy(isDefault = false) }
        assertTrue(r is Outcome.Success)
    }

    // ─── Invariant 3: case-insensitive name uniqueness ────────────────────

    @Test
    fun create_returns_NameAlreadyExists_for_duplicate() = runTest {
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(config("home", isDefault = true)),
        )
        val r = store.create(config("home", isDefault = false))
        assertEquals(Outcome.Failure(StoreError.NameAlreadyExists("home")), r)
    }

    @Test
    fun create_returns_NameAlreadyExists_for_case_insensitive_duplicate() = runTest {
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(config("Home", isDefault = true)),
        )
        val r = store.create(config("HOME", isDefault = false))
        assertEquals(Outcome.Failure(StoreError.NameAlreadyExists("HOME")), r)
    }

    // ─── Invariant 4: activeDeviceIds + orphanedAt lifecycle ──────────────

    @Test
    fun applyToCurrentDevice_adds_device_to_active_set() = runTest {
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(config("only", isDefault = true, devices = setOf(device1))),
        )
        val r = store.applyToCurrentDevice("only", device2)
        assertTrue(r is Outcome.Success)
        assertEquals(setOf(device1, device2), store.snapshot()[0].activeDeviceIds)
    }

    @Test
    fun applyToCurrentDevice_clears_orphanedAt_if_was_orphan() = runTest {
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(
                config("only", isDefault = true, devices = emptySet(), orphanedAt = 1_700_000_000_000L),
            ),
        )
        val r = store.applyToCurrentDevice("only", device1)
        assertTrue(r is Outcome.Success)
        val updated = store.snapshot()[0]
        assertEquals(setOf(device1), updated.activeDeviceIds)
        assertNull(updated.orphanedAt, "orphanedAt should be cleared on apply: $updated")
    }

    @Test
    fun removeFromCurrentDevice_sets_orphanedAt_when_last_device() = runTest {
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(
                config("active", isDefault = true, devices = setOf(device1)),
                // need another default-capable active config so removeFromCurrentDevice
                // doesn't refuse with DefaultMustExist.
                config("backup", isDefault = false, devices = setOf(device2)),
            ),
        )
        // First demote "active" so removing its only device doesn't violate
        // FR-003a (need another default). Mark backup as default first.
        store.markDefault("backup").let {
            assertTrue(it is Outcome.Success)
        }

        val r = store.removeFromCurrentDevice("active", device1, nowMillis = 1_800_000_000_000L)
        assertTrue(r is Outcome.Success, "expected Success, got: $r")

        val updated = store.snapshot().first { it.configName == "active" }
        assertEquals(emptySet(), updated.activeDeviceIds)
        assertEquals(1_800_000_000_000L, updated.orphanedAt)
        assertTrue(updated.isOrphan, "should be marked ORPHAN")
    }

    @Test
    fun removeFromCurrentDevice_refuses_if_default_would_become_orphan_with_no_other_default() = runTest {
        // Single default config; removing its only device would leave store
        // без any active default → DefaultMustExist.
        val store = FakeNamedConfigsLocalStore(
            initial = listOf(config("only", isDefault = true, devices = setOf(device1))),
        )
        val r = store.removeFromCurrentDevice("only", device1, nowMillis = 1L)
        assertEquals(Outcome.Failure(StoreError.DefaultMustExist), r)
    }

    @Test
    fun isOrphan_derived_correctly() {
        val active = NamedConfig(
            configName = "a", isDefault = true, presetId = "workspace",
            deviceClass = "phone", activeDeviceIds = setOf(device1), orphanedAt = null,
        )
        val orphan = NamedConfig(
            configName = "o", isDefault = false, presetId = "workspace",
            deviceClass = "phone", activeDeviceIds = emptySet(), orphanedAt = 1L,
        )
        assertEquals(true, active.isActive)
        assertEquals(false, active.isOrphan)
        assertEquals(false, orphan.isActive)
        assertEquals(true, orphan.isOrphan)
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun <T, E> Outcome<T, E>.orFail(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("expected Success, got Failure($error)")
    }
}
