package com.launcher.app.preset.task126

import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import com.launcher.preset.model.Vendor
import com.launcher.preset.model.VendorRecipeCatalogue
import com.launcher.preset.port.VendorDetector
import com.launcher.preset.port.VendorRecipeSource

/**
 * TASK-73 test doubles, module-local to `:app`'s JVM test source set. `:core`'s
 * `commonTest` fakes (`com.launcher.test.fakes.*`) aren't visible here — no
 * test-fixtures wiring between `:core` and `:app` — same reason
 * [com.launcher.app.data.recovery.AuthAvailabilityAndroidImplTest] rolls its
 * own local `StubGmsPort` instead of reusing the core one.
 */
class FakeVendorDetector(var vendor: Vendor) : VendorDetector {
    override fun detect(): Vendor = vendor
}

class FakeVendorRecipeSource(var catalogue: VendorRecipeCatalogue = VendorRecipeCatalogue()) : VendorRecipeSource {
    override suspend fun loadCatalogue(): VendorRecipeCatalogue = catalogue
}

class FakeGmsAvailabilityPort(var status: GmsStatus) : GmsAvailabilityPort {
    override suspend fun status(): GmsStatus = status
}
