package com.launcher.preset.wire

import com.launcher.preset.model.VendorOverride
import com.launcher.preset.model.VendorRecipeCatalogue
import com.launcher.preset.model.filterKnown
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TASK-73 (FR-006, FR-007, contracts/vendor-recipe-catalogue.md "Tests").
 *
 * Fixture mirrored for human readability at
 * `core/src/commonTest/resources/fixtures/vendor-recipes-v1.json` — inlined as
 * strings here per this project's KMP commonTest convention (see
 * `NamedConfigWireFormatTest`), not read from disk.
 */
class VendorRecipeCatalogueWireFormatTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun v1Catalogue_withThreeVendorOverrides_roundtrips() {
        val catalogue = VendorRecipeCatalogue(
            schemaVersion = 1,
            entries = mapOf(
                "LauncherRole" to mapOf(
                    "Xiaomi" to VendorOverride(
                        intentAction = "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
                        intentPackage = "com.android.settings",
                        intentClassName = "com.android.settings.Settings\$ManageDefaultAppsActivitiesActivity",
                        fallbackTextKey = "launcher_role.fallback.xiaomi",
                    ),
                    "Huawei" to VendorOverride(
                        intentAction = "android.settings.HOME_SETTINGS",
                        fallbackTextKey = "launcher_role.fallback.huawei",
                    ),
                    "Samsung" to VendorOverride(
                        intentPackage = "com.android.settings",
                        intentClassName = "com.android.settings.Settings\$DefaultAppSettingsActivity",
                        fallbackTextKey = "launcher_role.fallback.samsung",
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(VendorRecipeCatalogue.serializer(), catalogue)
        val decoded = json.decodeFromString(VendorRecipeCatalogue.serializer(), encoded)

        assertEquals(catalogue, decoded)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(3, decoded.entries.getValue("LauncherRole").size)
    }

    @Test
    fun unknownComponentTypeKey_isDropped_doesNotFailParse() {
        val wire = """
            {
              "schemaVersion": 1,
              "entries": {
                "LauncherRole": {
                  "Xiaomi": { "fallbackTextKey": "launcher_role.fallback.xiaomi" }
                },
                "SomeFutureComponent": {
                  "Xiaomi": { "fallbackTextKey": "some_future.fallback.xiaomi" }
                }
              }
            }
        """.trimIndent()

        // Decode itself must not fail on the unrecognized outer key.
        val decoded = json.decodeFromString(VendorRecipeCatalogue.serializer(), wire)
        assertEquals(setOf("LauncherRole", "SomeFutureComponent"), decoded.entries.keys)

        // The post-decode filter (FR-007) drops it — only "LauncherRole" is wired in v1.
        val filtered = decoded.filterKnown(knownComponentTypes = setOf("LauncherRole"))
        assertEquals(setOf("LauncherRole"), filtered.entries.keys)
        assertEquals(
            "launcher_role.fallback.xiaomi",
            filtered.entries.getValue("LauncherRole").getValue("Xiaomi").fallbackTextKey,
        )
    }

    @Test
    fun unknownVendorNameKey_isDropped_doesNotFailParse() {
        val wire = """
            {
              "schemaVersion": 1,
              "entries": {
                "LauncherRole": {
                  "Xiaomi": { "fallbackTextKey": "launcher_role.fallback.xiaomi" },
                  "Oppo": { "fallbackTextKey": "launcher_role.fallback.oppo" }
                }
              }
            }
        """.trimIndent()

        // Decode itself must not fail on the unrecognized Vendor name.
        val decoded = json.decodeFromString(VendorRecipeCatalogue.serializer(), wire)
        assertEquals(setOf("Xiaomi", "Oppo"), decoded.entries.getValue("LauncherRole").keys)

        // The post-decode filter (FR-007) drops it — "Oppo" is not a Vendor.entries name.
        val filtered = decoded.filterKnown(knownComponentTypes = setOf("LauncherRole"))
        assertEquals(setOf("Xiaomi"), filtered.entries.getValue("LauncherRole").keys)
    }
}
