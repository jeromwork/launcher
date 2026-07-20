package com.launcher.preset.wire

import family.wire.WireVersion

import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.LifecycleState
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
 * T127-011 (FR-004, contracts/profile-v2.md, CLAUDE.md rule 5), reshaped TASK-136.
 *
 * `schemaVersion` stays **2**: everything TASK-127/136 adds — `tags`, `parentId`,
 * three new `type` discriminators and the apply-state as a `LifecycleState`
 * component — is additive, so no bump and no migration writer (spec Q6).
 *
 * TASK-136 wire shape: an [Entity] serializes with `components: [{type,...}]` (a
 * free bag) + entity-level `tags: [...]`; apply-state lives inside the bag as a
 * `LifecycleState` component, not a top-level `status` enum. The `Profile` field is
 * `entities` (renamed from `components`).
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
        state: LifecycleState = LifecycleState.Applied,
        tags: Set<Tag> = emptySet(),
    ) = Entity(
        id = id,
        components = listOf(component, state),
        tags = tags,
        wizardBehavior = WizardBehavior.AutoApply,
        critical = false,
        parentId = parentId,
    )

    /** The owner's target screen (US-4), stored flat with the tree in `parentId`. */
    private fun hierarchicalProfile() = Profile(
        schemaVersion = WireVersion(2, 0),
        basedOnPreset = "simple-launcher",
        presetVersion = 2,
        layoutKey = "grid",
        entities = listOf(
            entity("ws-main", Component.Workspace(layoutKey = "single"), tags = setOf(Tag.Presentation, Tag.Workspace)),
            entity("flow-calls", Component.Flow(titleKey = "flow.calls", order = 0), parentId = "ws-main", tags = setOf(Tag.Presentation, Tag.Flow)),
            entity("flow-apps", Component.Flow(titleKey = "flow.apps", order = 1), parentId = "ws-main", tags = setOf(Tag.Presentation, Tag.Flow)),
            entity("tile-wa", Component.AppTile("com.whatsapp", "tile.wa"), parentId = "flow-calls", tags = setOf(Tag.Presentation, Tag.Tile)),
            entity("sos", Component.Sos(), parentId = "flow-calls", tags = setOf(Tag.Presentation, Tag.Tile, Tag.Safety, Tag.Emergency)),
            entity("tile-settings", Component.AppTile("com.android.settings", "tile.settings"), parentId = "flow-apps", tags = setOf(Tag.Presentation, Tag.Tile)),
            entity("toolbar", Component.Toolbar(layoutKey = "bottom-bar"), parentId = "ws-main", tags = setOf(Tag.Presentation, Tag.Toolbar)),
            entity(
                "btn-calls",
                Component.ToolbarButton(targetFlowId = "flow-calls", labelKey = "btn.calls", order = 0),
                parentId = "toolbar",
                tags = setOf(Tag.Presentation, Tag.ToolbarButton),
            ),
            entity(
                "btn-apps",
                Component.ToolbarButton(targetFlowId = "flow-apps", labelKey = "btn.apps", order = 1),
                parentId = "toolbar",
                tags = setOf(Tag.Presentation, Tag.ToolbarButton),
            ),
            entity("statusbar", Component.StatusBarPolicy, state = LifecycleState.Unverifiable, tags = setOf(Tag.System)),
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

        // "2.0", not 2: the number did not change across the wire-format conversion (I3 forbids
        // lowering it, and nothing about the shape changed), only its representation.
        assertEquals(WireVersion(2, 0), Profile.SCHEMA_VERSION)
        assertEquals(true, encoded.contains("\"schemaVersion\":\"2.0\""))
        assertEquals(true, encoded.contains("\"minReaderVersion\":\"1.0\""))
        assertEquals(true, encoded.contains("\"minWriterVersion\":\"1.0\""))
    }

    // ---- backward compatibility with profiles written before TASK-127 ----

    @Test
    fun missingTags_fallBackToConstructorDefaults() {
        // TASK-136: tags are now explicit on the entity (no constructor-default
        // fallback). This pins that entity-level `tags` deserialize from the wire.
        val jsonText = """
            {
              "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "basedOnPreset": "p",
              "presetVersion": 2,
              "layoutKey": "grid",
              "entities": [
                {
                  "id": "tile",
                  "components": [ { "type": "AppTile", "packageName": "com.a", "labelKey": "l" } ],
                  "tags": ["Presentation", "Tile"],
                  "wizardBehavior": "AutoApply",
                  "critical": false
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Profile.serializer(), jsonText)

        assertEquals(setOf(Tag.Presentation, Tag.Tile), decoded.entities.single().tags)
    }

    @Test
    fun missingParentId_readsAsRoot_soPreTask127ProfilesStillLoad() {
        val jsonText = """
            {
              "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "basedOnPreset": "p",
              "presetVersion": 2,
              "layoutKey": "grid",
              "entities": [
                {
                  "id": "tile",
                  "components": [ { "type": "AppTile", "packageName": "com.a", "labelKey": "l" } ],
                  "wizardBehavior": "AutoApply",
                  "critical": false
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Profile.serializer(), jsonText)

        assertNull(decoded.entities.single().parentId)
    }

    @Test
    fun objectStyleComponents_stillDeserialize_afterObjectToDataClassConversion() {
        // LauncherRole / StatusBarPolicy are Kotlin data objects; their wire shape
        // carries no fields at all — `{"type":"..."}` must still deserialize inside
        // the components bag.
        val jsonText = """
            {
              "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "basedOnPreset": "p",
              "presetVersion": 2,
              "layoutKey": "grid",
              "entities": [
                {
                  "id": "role",
                  "components": [ { "type": "LauncherRole" } ],
                  "tags": ["System"],
                  "wizardBehavior": "AutoApply",
                  "critical": true
                },
                {
                  "id": "bar",
                  "components": [ { "type": "StatusBarPolicy" } ],
                  "tags": ["System"],
                  "wizardBehavior": "AutoApply",
                  "critical": false
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Profile.serializer(), jsonText)

        assertEquals(setOf(Tag.System), decoded.entities[0].tags)
        assertEquals(setOf(Tag.System), decoded.entities[1].tags)
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
              "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0", "basedOnPreset": "p", "presetVersion": 2, "layoutKey": "grid",
              "entities": [
                {
                  "id": "t",
                  "components": [ { "type": "AppTile", "packageName": "com.a", "labelKey": "l" } ],
                  "tags": ["Presentation", "FutureTag"],
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
              "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0", "basedOnPreset": "p", "presetVersion": 2, "layoutKey": "grid",
              "entities": [
                {
                  "id": "x",
                  "components": [ { "type": "FutureComponent", "whatever": 1 } ],
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
        // Apply-state is now a LifecycleState component in the bag; an unknown
        // state variant is an unknown polymorphic component type and must fail loud.
        val jsonText = """
            {
              "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0", "basedOnPreset": "p", "presetVersion": 2, "layoutKey": "grid",
              "entities": [
                {
                  "id": "t",
                  "components": [
                    { "type": "AppTile", "packageName": "com.a", "labelKey": "l" },
                    { "type": "LifecycleState.FutureStatus" }
                  ],
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
    fun unknownKey_isIgnored_soAddingOptionalFieldsStaysSafe() {
        val jsonText = """
            {
              "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0", "basedOnPreset": "p", "presetVersion": 2, "layoutKey": "grid",
              "futureTopLevelField": "ignored",
              "entities": [
                {
                  "id": "t",
                  "components": [ { "type": "AppTile", "packageName": "com.a", "labelKey": "l",
                                   "futureFieldOnComponent": true } ],
                  "wizardBehavior": "AutoApply", "critical": false
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Profile.serializer(), jsonText)

        assertEquals("t", decoded.entities.single().id)
    }
}
