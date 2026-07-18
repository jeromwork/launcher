package com.launcher.preset.wire

import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.FailReason
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Tag
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * TASK-136 T136-017 / T136-018 / T136-019 (SC-003, contract profile-serialization.md).
 *
 * Entity-grouped wire format (mirror of Fleks `Snapshot`): each entity groups its
 * polymorphic `components` list + its `tags`. kotlinx polymorphic,
 * `classDiscriminator="type"` — zero custom serializer. Config mirrors
 * `DataStoreProfileStore` exactly.
 */
class ProfileSerializationRoundtripTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun mixedProfile(): Profile = Profile(
        basedOnPreset = "simple-launcher",
        presetVersion = 1,
        layoutKey = "single",
        entities = listOf(
            Entity("ws-main", listOf(Component.Workspace()), setOf(Tag.Presentation, Tag.Workspace)),
            Entity(
                "flow-a",
                listOf(Component.Flow(titleKey = "a", order = 0)),
                setOf(Tag.Presentation, Tag.Flow),
                parentId = "ws-main",
            ),
            Entity(
                "flow-b",
                listOf(Component.Flow(titleKey = "b", order = 1)),
                setOf(Tag.Presentation, Tag.Flow),
                parentId = "ws-main",
            ),
            // The mixed entity: a data component AND a LifecycleState AND ≥3 tags.
            Entity(
                "tile-wa",
                listOf(
                    Component.AppTile(packageName = "com.whatsapp", labelKey = "wa"),
                    LifecycleState.Applied,
                ),
                setOf(Tag.Presentation, Tag.Tile, Tag.Communication),
                parentId = "flow-a",
            ),
            Entity(
                "tile-settings",
                listOf(Component.AppTile(packageName = "com.android.settings", labelKey = "s"), LifecycleState.Pending),
                setOf(Tag.Presentation, Tag.Tile),
                parentId = "flow-a",
            ),
            Entity("toolbar", listOf(Component.Toolbar(layoutKey = "bar")), setOf(Tag.Presentation, Tag.Toolbar), parentId = "ws-main"),
            Entity(
                "btn-a",
                listOf(Component.ToolbarButton(targetFlowId = "flow-a", labelKey = "a", order = 0), LifecycleState.Applied),
                setOf(Tag.Presentation, Tag.ToolbarButton),
                parentId = "toolbar",
            ),
            Entity(
                "btn-b",
                listOf(
                    Component.ToolbarButton(targetFlowId = "flow-b", labelKey = "b", order = 1),
                    LifecycleState.Failed(FailReason.PermissionDenied("android.permission.X")),
                ),
                setOf(Tag.Presentation, Tag.ToolbarButton),
                parentId = "toolbar",
            ),
        ),
    )

    /** T136-017 — Profile → JSON → Profile is identity. */
    @Test
    fun roundtripsMixedProfile() {
        val original = mixedProfile()
        val encoded = json.encodeToString(Profile.serializer(), original)
        val decoded = json.decodeFromString(Profile.serializer(), encoded)
        assertEquals(original, decoded)
    }

    /** T136-018 — components serialize as one polymorphic array inside the entity. */
    @Test
    fun componentsSerializeAsOnePolymorphicArrayInsideEntity() {
        val entity = Entity(
            "tile-wa",
            listOf(Component.AppTile(packageName = "com.whatsapp", labelKey = "wa"), LifecycleState.Applied),
            setOf(Tag.Presentation, Tag.Tile),
        )
        val encoded = json.encodeToString(Entity.serializer(), entity)
        // One "components" array carrying both discriminated objects — not separate
        // per-type top-level tables.
        assertTrue(encoded.contains("\"components\""), "entity must group its components: $encoded")
        assertTrue(encoded.contains("\"type\":\"AppTile\""), encoded)
        assertTrue(encoded.contains("\"type\":\"LifecycleState.Applied\""), encoded)
        // The polymorphic array holds both — assert both appear after "components".
        val idx = encoded.indexOf("\"components\"")
        assertTrue(idx >= 0)
        val afterComponents = encoded.substring(idx)
        assertTrue(afterComponents.contains("AppTile") && afterComponents.contains("LifecycleState.Applied"))
    }

    /** T136-019 — unknown component `type` fails loud. */
    @Test
    fun unknownComponentTypeFailsLoud() {
        val badJson = """
            {"id":"x","components":[{"type":"NotAComponent","foo":1}],"tags":[]}
        """.trimIndent()
        assertFailsWith<SerializationException> {
            json.decodeFromString(Entity.serializer(), badJson)
        }
    }

    /** T136-019 — unknown Tag value fails loud (no per-element enum leniency). */
    @Test
    fun unknownTagValueFailsLoud() {
        val badJson = """
            {"id":"x","components":[],"tags":["NotATag"]}
        """.trimIndent()
        assertFailsWith<SerializationException> {
            json.decodeFromString(Entity.serializer(), badJson)
        }
    }
}
