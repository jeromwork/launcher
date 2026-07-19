package com.launcher.test.fakes

import com.launcher.preset.model.Vendor
import com.launcher.preset.port.VendorDetector

/**
 * Programmable [VendorDetector] for TASK-73 unit tests (CLAUDE.md rule 6 —
 * mock-first). Mutate [vendor] between invocations to simulate different
 * devices.
 */
class FakeVendorDetector(
    var vendor: Vendor = Vendor.GenericAndroid,
) : VendorDetector {
    override fun detect(): Vendor = vendor
}
