package com.launcher.adapters.config

import android.content.Context
import android.util.Log

/**
 * Spec 008 FR-045: cleanup of legacy spec 003 mock-storage on first launch.
 *
 * **CLEANUP-008**: legacy spec 003 mock-storage cleanup. Safe to remove this file
 * после 2026-12-31 (by then все dev devices upgraded past 008 epoch).
 *
 * **Status update — Spec 010 ARCH-016 closure (2026-05-19)**: `MockFlowRepository`
 * AND the `flows_mock_*.json` assets it parsed are **deleted** (spec 010 T031 /
 * T032 / FR-004). Production now reads layout from `/links/{linkId}/config/current`
 * via [ConfigBackedFlowRepository] in both `realBackend` and `mockBackend`
 * flavors — mockBackend wires it on top of `FakeConfigEditor` +
 * `FakeRemoteSyncBackend` (spec 007 pattern).
 *
 * **Revised scope** (per T005 inventory в legacy-cleanup-inventory.md):
 * spec 003's mock storage was **read-only APK assets**, NOT user-writable files.
 * There is nothing to delete from `context.filesDir`. This class stays as a
 * no-op marker documenting that the cleanup was architectural (file-deletion +
 * DI rewire), not file-level. Kept so future readers grepping for
 * "spec 003 legacy" or "MockFlowRepository" land on this rationale.
 *
 * If actually mutable user-files appear на 003-era devices (which can't happen
 * per T005 inventory), the `cleanupOnce()` method below can be activated.
 */
class LegacyMockStorageCleanup(@Suppress("unused") private val context: Context) {

    fun cleanupOnce() {
        // CLEANUP-008: per T005 inventory, no user-writable spec 003 files exist
        // — this is intentionally a no-op. See legacy-cleanup-inventory.md.
        Log.d(TAG, "CLEANUP-008 invoked — no-op (per T005 inventory, no legacy files exist)")
    }

    companion object {
        private const val TAG = "ConfigSyncCleanup"
    }
}
