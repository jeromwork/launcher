package com.launcher.preset.engine

import com.launcher.preset.ecs.get
import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.Blueprint
import com.launcher.preset.model.Component
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Tag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TASK-136 T136-036 (FR-015c) — `paramsOverride` merge is schema-roundtrip stable:
 * override → spawn → the resolved component matches the expected value and
 * survives serialization unchanged. Carries the TASK-127 paramsOverride coverage
 * onto the free-bag shape.
 */
class ParamsOverrideFitnessTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun paramsOverride_mergesIntoSpawnedComponent_andRoundtrips() {
        val pool = Pool(
            declarations = listOf(
                Blueprint(
                    id = "font",
                    components = listOf(Component.FontSize(scale = 1.0f)),
                    tags = setOf(Tag.Appearance, Tag.Accessibility),
                ),
            ),
        )
        val preset = Preset(
            presetId = "p",
            version = 1,
            layoutKey = "single",
            activeComponents = listOf(
                ActiveComponentEntry(
                    poolRef = "font",
                    paramsOverride = JsonObject(mapOf("scale" to JsonPrimitive(2.5f))),
                ),
            ),
        )

        val profile = ProfileFactory().create(preset, pool)
        val font = profile.entities.single { it.id == "font" }.get<Component.FontSize>()
        assertEquals(2.5f, font?.scale, "paramsOverride must merge into the spawned component")

        // Roundtrip-stable: the overridden component survives serialize → deserialize.
        val encoded = json.encodeToString(Component.serializer(), font!!)
        val decoded = json.decodeFromString(Component.serializer(), encoded)
        assertEquals(font, decoded)
    }
}
