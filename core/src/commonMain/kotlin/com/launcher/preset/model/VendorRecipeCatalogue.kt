package com.launcher.preset.model

import kotlinx.serialization.Serializable

/**
 * TASK-73 (FR-005, FR-006) — bundled recipe catalogue of vendor-specific
 * `Provider.apply()` intent overrides, keyed by a [Component]'s existing
 * `@SerialName` discriminator (outer key, e.g. `"LauncherRole"`) and by
 * [Vendor.name] (inner key, e.g. `"Xiaomi"`).
 *
 * Mirrors the `Pool`/`Preset` `schemaVersion` pattern (see `Pool.kt`). Not a
 * shareable/user-facing artifact (CLAUDE.md rule 9 does not apply) — see
 * contracts/vendor-recipe-catalogue.md "Not a shareable/user-facing artifact".
 */
@Serializable
data class VendorRecipeCatalogue(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val entries: Map<String, Map<String, VendorOverride>> = emptyMap(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * One `(componentType, vendor)` override — vendor-specific intent target
 * (all fields optional; a partial override e.g. `fallbackTextKey`-only is
 * valid, see contracts doc) plus the fallback text key used when neither the
 * vendor intent nor the generic path resolves.
 */
@Serializable
data class VendorOverride(
    val intentAction: String? = null,
    val intentPackage: String? = null,
    val intentClassName: String? = null,
    val intentCategory: String? = null,
    val fallbackTextKey: String? = null,
)

/**
 * TASK-73 (FR-007) — post-decode filter step dropping unknown outer
 * (`componentType`) and inner ([Vendor] name) keys. Kept as a pure,
 * `commonMain`-testable function separate from decode itself: `Map<String,
 * Map<String, VendorOverride>>` has no notion of "known" keys at the JSON
 * level (`ignoreUnknownKeys` only covers unknown *fields*, not unknown *map
 * keys* — see contracts/vendor-recipe-catalogue.md). Called by
 * `BundledVendorRecipeSource` after decode with the component-type
 * discriminators it actually wires a `Provider` for (`"LauncherRole"` in v1
 * — adding coverage for a new `Component` subtype is itself a code change,
 * so growing this whitelist alongside it is not premature abstraction).
 */
fun VendorRecipeCatalogue.filterKnown(
    knownComponentTypes: Set<String>,
    knownVendorNames: Set<String> = Vendor.entries.map { it.name }.toSet(),
): VendorRecipeCatalogue = copy(
    entries = entries
        .filterKeys { it in knownComponentTypes }
        .mapValues { (_, vendorOverrides) -> vendorOverrides.filterKeys { it in knownVendorNames } },
)
