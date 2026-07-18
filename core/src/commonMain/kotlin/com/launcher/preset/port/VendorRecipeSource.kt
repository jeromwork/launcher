package com.launcher.preset.port

import com.launcher.preset.model.VendorRecipeCatalogue

/**
 * TASK-73 (FR-005) — loads the bundled vendor-recipe catalogue, by the same
 * one-port-one-artifact convention as [PoolSource]/[PresetSource]/[HintPoolSource].
 *
 * No `TODO(shareability)` here (CLAUDE.md rule 9 does not apply) — this is
 * infrastructure data (OEM intent targets), not a user/preset-authored
 * artifact; see contracts/vendor-recipe-catalogue.md "Not a shareable/
 * user-facing artifact".
 */
interface VendorRecipeSource {
    suspend fun loadCatalogue(): VendorRecipeCatalogue
}
