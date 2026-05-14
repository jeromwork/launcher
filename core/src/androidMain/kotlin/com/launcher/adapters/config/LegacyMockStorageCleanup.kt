package com.launcher.adapters.config

import android.content.Context
import android.util.Log

/**
 * Spec 008 FR-045: cleanup of legacy spec 003 mock-storage on first launch.
 *
 * **CLEANUP-008**: legacy spec 003 mock-storage cleanup. Safe to remove this file
 * после 2026-12-31 (by then все dev devices upgraded past 008 epoch).
 *
 * **Revised scope** (per T005 inventory в legacy-cleanup-inventory.md):
 * spec 003's mock storage is **read-only APK assets**, NOT user-writable files.
 * There is nothing to delete from `context.filesDir`. Instead:
 *
 *  - `realBackend` flavor MUST NOT use `MockFlowRepository` once spec 008 ships —
 *    DI wiring change handled в Phase 8 (T100 Settings/FlowRepository binding).
 *  - `mockBackend` flavor continues to use MockFlowRepository for dev/test.
 *
 * This class is a **no-op marker** documenting that cleanup is conceptual
 * (architectural switch), not file-level. Kept so future readers grepping for
 * "spec 003 legacy" land on this rationale.
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
