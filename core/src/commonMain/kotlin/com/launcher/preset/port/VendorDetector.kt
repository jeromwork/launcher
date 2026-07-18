package com.launcher.preset.port

import com.launcher.preset.model.Vendor

/**
 * TASK-73 (FR-001) — determines the current device's [Vendor]. Android
 * adapter reads `Build.MANUFACTURER`, never exposed here (CLAUDE.md rule 1).
 *
 * Synchronous: the underlying platform read is a static field, no I/O.
 */
interface VendorDetector {
    fun detect(): Vendor
}
