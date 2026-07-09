package com.launcher.api.wizard.data

import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSourceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WireFormatRoundtripTest {

    @Test
    fun wizardManifest_roundtrip() {
        val original = ConfigDocument.Manifest(
            header = ConfigDocumentHeader(
                schemaVersion = 2,
                id = "wizard-manifest.simple-launcher",
                name = "wizard_manifest.simple_launcher.name",
                description = "wizard_manifest.simple_launcher.desc",
                deviceClass = listOf("android-phone"),
            ),
            body = WizardManifestBody(
                autoOrder = true,
                steps = null,
            ),
        )
        val encoded = ConfigParser.encode(original)
        val result = ConfigParser.parse(ConfigKind.WizardManifest, encoded)
        assertIs<ConfigSourceResult.Success>(result)
        assertEquals(original, result.document)
    }

    @Test
    fun screenLayout_roundtrip() {
        val original = ConfigDocument.Layout(
            header = ConfigDocumentHeader(1, "screen-layout.3x4", "name.k", "desc.k", listOf("*")),
            body = ScreenLayoutBody(
                gridRows = 4,
                gridCols = 3,
                bottomToolbar = ToolbarSpec(actions = listOf("back", "settings")),
                topTabs = listOf(TabSpec(labelKey = "tab.home", iconKey = "icon.home")),
            ),
        )
        val encoded = ConfigParser.encode(original)
        val result = ConfigParser.parse(ConfigKind.ScreenLayout, encoded)
        assertIs<ConfigSourceResult.Success>(result)
        assertEquals(original, result.document)
    }

    @Test
    fun tileSet_roundtrip() {
        val original = ConfigDocument.TileSetDoc(
            header = ConfigDocumentHeader(1, "tile-set.classic-6", "name.k", "desc.k", listOf("*")),
            body = TileSetBody(
                tiles = listOf(
                    TileSpec(GridPosition(0, 0), "phone.call", "tile.maria.label", "icon.contact_woman"),
                    TileSpec(GridPosition(0, 1), "messenger.open", "tile.messages.label", "icon.messages"),
                ),
            ),
        )
        val encoded = ConfigParser.encode(original)
        val result = ConfigParser.parse(ConfigKind.TileSet, encoded)
        assertIs<ConfigSourceResult.Success>(result)
        assertEquals(original, result.document)
    }

    @Test
    fun systemSettingsPool_roundtrip() {
        val original = ConfigDocument.SystemSettingsPoolDoc(
            header = ConfigDocumentHeader(1, "system-settings.android-pool", "n", "d", listOf("android-phone")),
            body = SystemSettingsPoolBody(
                platform = "android",
                settings = listOf(
                    SystemSettingEntry(
                        id = "android.role.home",
                        mechanism = WireSettingMechanism.DeepLink,
                        criticality = WireCriticality.Required,
                        canSkip = true,
                        deepLink = "RoleManager.createRequestRoleIntent(ROLE_HOME)",
                        androidMinApi = 29,
                        dependsOn = emptyList(),
                        detectionStrategy = WireDetectionStrategy.Programmatic,
                        labelKey = "system_setting.role_home.label",
                        descriptionKey = "system_setting.role_home.desc",
                        extendedInstructionKey = null,
                    ),
                ),
            ),
        )
        val encoded = ConfigParser.encode(original)
        val result = ConfigParser.parse(ConfigKind.SystemSettingsPool, encoded)
        assertIs<ConfigSourceResult.Success>(result)
        assertEquals(original, result.document)
    }

    @Test
    fun uiCustomizationPool_roundtrip() {
        val original = ConfigDocument.UICustomizationPoolDoc(
            header = ConfigDocumentHeader(1, "ui-customization.ui-pool", "n", "d", listOf("*")),
            body = UICustomizationPoolBody(
                platform = "*",
                options = listOf(
                    UIOptionEntry(
                        id = "language",
                        kind = WireUIOptionKind.SimpleChoice,
                        questionKey = "ui.language.question",
                        descriptionKey = null,
                        criticality = WireCriticality.Required,
                        defaultValue = "en",
                        choices = listOf(
                            Choice("en", "ui.language.en"),
                            Choice("ru", "ui.language.ru"),
                        ),
                        choicesFrom = null,
                    ),
                    UIOptionEntry(
                        id = "tileSet",
                        kind = WireUIOptionKind.PickFromBundled,
                        questionKey = "ui.tileSet.question",
                        descriptionKey = null,
                        criticality = WireCriticality.Required,
                        defaultValue = "classic-6",
                        choices = null,
                        choicesFrom = ChoicesFromRef(WireConfigKind.TileSet, filter = null),
                    ),
                ),
            ),
        )
        val encoded = ConfigParser.encode(original)
        val result = ConfigParser.parse(ConfigKind.UICustomizationPool, encoded)
        assertIs<ConfigSourceResult.Success>(result)
        assertEquals(original, result.document)
    }

    @Test
    fun forwardCompat_unknownAdditiveField_dropped() {
        val withUnknownField = """
            {
              "schemaVersion": 1,
              "id": "tile-set.x",
              "name": "name.k",
              "description": "desc.k",
              "deviceClass": ["*"],
              "futureTopLevelField": "ignored",
              "body": {
                "tiles": [{
                  "position": {"row": 0, "col": 0},
                  "actionType": "phone.call",
                  "labelKey": "l",
                  "iconKey": "i",
                  "futureField": 42
                }]
              }
            }
        """.trimIndent()
        val result = ConfigParser.parse(ConfigKind.TileSet, withUnknownField)
        assertIs<ConfigSourceResult.Success>(result)
        val doc = assertIs<ConfigDocument.TileSetDoc>(result.document)
        assertEquals(1, doc.body.tiles.size)
        assertEquals("phone.call", doc.body.tiles[0].actionType)
    }

    @Test
    fun hardFail_futureSchemaVersion() {
        val futureVersion = """
            {
              "schemaVersion": 999,
              "id": "tile-set.future",
              "name": "n",
              "description": "d",
              "deviceClass": ["*"],
              "body": {"tiles": []}
            }
        """.trimIndent()
        val result = ConfigParser.parse(ConfigKind.TileSet, futureVersion)
        assertIs<ConfigSourceResult.IncompatibleVersion>(result)
        assertEquals(999, result.found)
        // KNOWN_VERSION bumped 1 → 2 by TASK-7 (pool schemaVersion 2; see
        // contracts/system-settings-pool-v2.md). Other kinds (TileSet etc.)
        // still ship at v1 but the global parser ceiling is now 2.
        assertEquals(2, result.known)
    }

    @Test
    fun parseError_missingHeader() {
        val noHeader = """{"body": {"tiles": []}}"""
        val result = ConfigParser.parse(ConfigKind.TileSet, noHeader)
        assertIs<ConfigSourceResult.ParseError>(result)
        assertTrue(result.reason.contains("header"))
    }
}
