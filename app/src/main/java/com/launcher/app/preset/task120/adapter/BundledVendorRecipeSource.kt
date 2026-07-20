package com.launcher.app.preset.task120.adapter

import family.wire.UnknownWireVersionException
import family.wire.accessFor

import android.content.Context
import android.util.Log
import com.launcher.preset.model.VendorRecipeCatalogue
import com.launcher.preset.model.filterKnown
import com.launcher.preset.port.VendorRecipeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException

/**
 * TASK-73 (FR-005). Reads `vendor-recipes.json` from Android assets, by the
 * same pattern as [BundledPoolSource]. No `TODO(shareability)` (CLAUDE.md
 * rule 9 does not apply — see contracts/vendor-recipe-catalogue.md).
 *
 * Read semantics (contracts/vendor-recipe-catalogue.md "Read semantics"):
 * missing file, malformed JSON, or an unsupported `schemaVersion` all resolve
 * to an empty [VendorRecipeCatalogue] rather than throwing — `LauncherRoleProvider`
 * treats "no recipe found" identically to "recipe explicitly empty", falling
 * back to its pre-TASK-73 generic-only behaviour.
 */
class BundledVendorRecipeSource(
    private val context: Context,
    private val assetPath: String = "preset/vendor-recipes.json",
) : VendorRecipeSource {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    override suspend fun loadCatalogue(): VendorRecipeCatalogue = withContext(Dispatchers.IO) {
        val text = try {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (e: FileNotFoundException) {
            return@withContext VendorRecipeCatalogue()
        }

        val decoded = try {
            json.decodeFromString(VendorRecipeCatalogue.serializer(), text)
        } catch (e: UnknownWireVersionException) {
            // Distinct from malformed (§8): the file is intact, its version is one we cannot read.
            Log.w(TAG, "vendor-recipes.json has an unreadable wire version, falling back", e)
            return@withContext VendorRecipeCatalogue()
        } catch (e: SerializationException) {
            Log.w(TAG, "vendor-recipes.json malformed, falling back to generic-only dispatch", e)
            return@withContext VendorRecipeCatalogue()
        }

        // Version header gate (wire-format.md §3) — a newer catalogue is still usable unless it
        // says it needs a newer reader; unknown component types are dropped by filterKnown below.
        try {
            decoded.accessFor(VendorRecipeCatalogue.SCHEMA_VERSION)
        } catch (e: Exception) {
            Log.w(TAG, "vendor-recipes.json needs a newer reader, falling back to generic-only dispatch", e)
            return@withContext VendorRecipeCatalogue()
        }

        decoded.filterKnown(knownComponentTypes = KNOWN_COMPONENT_TYPES)
    }

    private companion object {
        const val TAG = "BundledVendorRecipeSource"

        /**
         * componentType discriminators this app currently wires a vendor-aware
         * `Provider` for (v1: `LauncherRole` only, FR-008). Extend alongside the
         * `Provider` that consumes the new coverage — not before.
         */
        val KNOWN_COMPONENT_TYPES = setOf("LauncherRole")
    }
}
