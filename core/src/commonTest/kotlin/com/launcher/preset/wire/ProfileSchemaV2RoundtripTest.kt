package com.launcher.preset.wire

import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Entity
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Tag
import com.launcher.preset.model.WizardBehavior
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * T127-011 (FR-004, contracts/profile-v2.md, CLAUDE.md rule 5).
 *
 * `schemaVersion` stays **2**: everything TASK-127 adds — `tags`, `parentId`,
 * three new `type` discriminators and the `Unverifiable` status — is additive, so
 * no bump and no migration writer (spec Q6).
 *
 * The Json settings below mirror `DataStoreProfileStore` exactly. A drift between
 * the two would void the roundtrip guarantee, so it is asserted here rather than
 * assumed.
 */
class ProfileSchemaV2RoundtripTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun entity(
        id: String,
        component: Component,
        parentId: String? = null,
        status: ComponentStatus = ComponentStatus.Applied,
    ) = Entity(
        id = id,
        component = component,
        wizardBehavior = WizardBehavior.AutoApply,
        critical = false,
        status = status,
        parentId = parentId,
    )

    /** The owner's target screen (US-4), stored flat with the tree in `parentId`. */
    private fun hierarchicalProfile() = Profile(
        schemaVersion = 2,
        basedOnPreset = "simple-launcher",
        presetVersion = 2,
        layoutKey = "grid",
        components = listOf(
            entity("ws-main", Component.Workspace(layoutKey = "single")),
            entity("flow-calls", Component.Flow(titleKey = "flow.calls", order = 0), parentId = "ws-main"),
            entity("flow-apps", Component.Flow(titleKey = "flow.apps", order = 1), parentId = "ws-main"),
            entity("tile-wa", Component.AppTile("com.whatsapp", "tile.wa"), parentId = "flow-calls"),
            entity("sos", Component.Sos(), parentId = "flow-calls"),
            entity("tile-settings", Component.AppTile("com.android.settings", "tile.settings"), parentId = "flow-apps"),
            entity("toolbar", Component.Toolbar(layoutKey = "bottom-bar"), parentId = "ws-main"),
            entity(
                "btn-calls",
                Component.ToolbarButton(targetFlowId = "flow-calls", labelKey = "btn.calls", order = 0),
                parentId = "toolbar",
            ),
            entity(
                "btn-apps",
                Component.ToolbarButton(targetFlowId = "flow-apps", labelKey = "btn.apps", order = 1),
                parentId = "toolbar",
            ),
            entity("statusbar", Component.StatusBarPolicy(), status = ComponentStatus.Unverifiable),
        ),
    )

    @Test
    fun v2Profile_withHierarchyTagsAndUnverifiable_roundtrips() {
        val profile = hierarchicalProfile()

        val encoded = json.encodeToString(Profile.serializer(), profile)
        val decoded = json.decodeFromString(Profile.serializer(), encoded)

        assertEquals(profile, decoded)
    }

    @Test
    fun schemaVersion_staysTwo_becauseEveryAdditionIsAdditive() {
        val encoded = json.encodeToString(Profile.serializer(), hierarchicalProfile())

        assertEquals(2, Profile.CURRENT_SCHEMA_VERSION)
        assertEquals(true, encoded.contains("\"schemaVersion\":2"))
    }

    // ---- backward compatibility with profiles written before TASK-127 ----

    @Test
    fun missingTags_fallBackToConstructorDefaults() {
        val jsonText = """
            {
              "schemaVersion": 2,
              "basedOnPreset": "p",
              "presetVersion": 2,
              "layoutKey": "grid",
              "components": [
                {
                  "id": "tile",
                  "component": { "type": "AppTile", "packageName": "com.a", "labelKey": "l" },
                  "wizardBehavior": "AutoApply",
                  "critical": false
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Profile.serializer(), jsonText)

        // Constructor defaults are the single source of truth for tags.
        assertEquals(setOf(Tag.Presentation, Tag.Tile), decoded.components.single().component.tags)
    }

    @Test
    fun missingParentId_readsAsRoot_soPreTask127ProfilesStillLoad() {
        val jsonText = """
            {
              "schemaVersion": 2,
              "basedOnPreset": "p",
              "presetVersion": 2,
              "layoutKey": "grid",
              "components": [
                {
                  "id": "tile",
                  "component": { "type": "AppTile", "packageName": "com.a", "labelKey": "l" },
                  "wizardBehavior": "AutoApply",
                  "critical": false
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Profile.serializer(), jsonText)

        assertNull(decoded.components.single().parentId)
    }

    @Test
    fun objectStyleComponents_stillDeserialize_afterObjectToDataClassConversion() {
        // LauncherRole / StatusBarPolicy were Kotlin objects before T127-007;
        // their old wire shape carries no fields at all.
        val jsonText = """
            {
              "schemaVersion": 2,
              "basedOnPreset": "p",
              "presetVersion": 2,
              "layoutKey": "grid",
              "components": [
                {
                  "id": "role",
                  "component": { "type": "LauncherRole" },
                  "wizardBehavior": "AutoApply",
                  "critical": true
                },
                {
                  "id": "bar",
                  "component": { "type": "StatusBarPolicy" },
                  "wizardBehavior": "AutoApply",
                  "critical": false
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Profile.serializer(), jsonText)

        assertEquals(setOf(Tag.System), decoded.components[0].component.tags)
        assertEquals(setOf(Tag.System), decoded.components[1].component.tags)
    }

    // ---- fail-loud pins (contract § Forward compat) ----
    //
    // These document a real limitation rather than hide it: kotlinx.serialization
    // has no per-element leniency for enum collections, so an OLDER reader dies on
    // a value a NEWER writer produced. Safe while a Profile never leaves the device
    // that wrote it; a lenient reader is mandatory before admin push / preset
    // sharing ship — TASK-131 owns that, and these tests will flip then.

    @Test
    fun unknownTagValue_failsLoud_untilLenientReaderShips() {
        val jsonText = """
            {
              "schemaVersion": 2, "basedOnPreset": "p", "presetVersion": 2, "layoutKey": "grid",
              "components": [
                {
                  "id": "t",
                  "component": { "type": "AppTile", "packageName": "com.a", "labelKey": "l",
                                 "tags": ["Presentation", "FutureTag"] },
                  "wizardBehavior": "AutoApply", "critical": false
                }
              ]
            }
        """.trimIndent()

        assertFailsWith<SerializationException> {
            json.decodeFromString(Profile.serializer(), jsonText)
        }
    }

    @Test
    fun unknownComponentType_failsLoud_untilLenientReaderShips() {
        val jsonText = """
            {
              "schemaVersion": 2, "basedOnPreset": "p", "presetVersion": 2, "layoutKey": "grid",
              "components": [
                {
                  "id": "x",
                  "component": { "type": "FutureComponent", "whatever": 1 },
                  "wizardBehavior": "AutoApply", "critical": false
                }
              ]
            }
        """.trimIndent()

        assertFailsWith<SerializationException> {
            json.decodeFromString(Profile.serializer(), jsonText)
        }
    }

    @Test
    fun unknownStatus_failsLoud_untilLenientReaderShips() {
        val jsonText = """
            {
              "schemaVersion": 2, "basedOnPreset": "p", "presetVersion": 2, "layoutKey": "grid",
              "components": [
                {
                  "id": "t",
                  "component": { "type": "AppTile", "packageName": "com.a", "labelKey": "l" },
                  "wizardBehavior": "AutoApply", "critical": false, "status": "FutureStatus"
                }
              ]
            }
        """.trimIndent()

        assertFailsWith<SerializationException> {
            json.decodeFromString(Profile.serializer(), jsonText)
        }
    }

    @Test
    fun unknownKey_isIgnored_soAddingOptionalFieldsStaysSafe() {
        val jsonText = """
            {
              "schemaVersion": 2, "basedOnPreset": "p", "presetVersion": 2, "layoutKey": "grid",
              "futureTopLevelField": "ignored",
              "components": [
                {
                  "id": "t",
                  "component": { "type": "AppTile", "packageName": "com.a", "labelKey": "l",
                                 "futureFieldOnComponent": true },
                  "wizardBehavior": "AutoApply", "critical": false
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Profile.serializer(), jsonText)

        assertEquals("t", decoded.components.single().id)
    }
}
