package com.launcher.test.fakes

import com.launcher.preset.model.VendorRecipeCatalogue
import com.launcher.preset.port.VendorRecipeSource

/**
 * Programmable [VendorRecipeSource] for TASK-73 unit tests (CLAUDE.md rule 6 —
 * mock-first). Mutate [catalogue] between invocations to simulate a recipe
 * update without touching the bundled asset.
 */
class FakeVendorRecipeSource(
    var catalogue: VendorRecipeCatalogue = VendorRecipeCatalogue(),
) : VendorRecipeSource {
    override suspend fun loadCatalogue(): VendorRecipeCatalogue = catalogue
}
