package com.launcher.preset.wire

import com.launcher.preset.model.Pool
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T024 (FR-014, contracts/pool-schema-v2.md, CLAUDE.md rule 5).
 *
 * Backward-compat: v1 pool.json (no `requires`, no `required` on declarations) →
 * decodes into v2 model with defaults (`requires == null`, `required == false`).
 */
class PoolSchemaV1ReadV2Test {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun v1PoolJson_decodesIntoV2Model_withDefaults() {
        // Hard-coded v1 wire format — declarations without requires/required.
        val v1Json = """
            {
              "schemaVersion": 1,
              "declarations": [
                {
                  "id": "font-tile",
                  "component": {
                    "type": "FontSize",
                    "scale": 1.6
                  },
                  "wizardBehavior": "Interactive",
                  "critical": false,
                  "descriptionKey": "pool.font.description"
                },
                {
                  "id": "sos-main",
                  "component": {
                    "type": "Sos",
                    "shareLocation": true,
                    "autoAnswer": true
                  },
                  "wizardBehavior": "Interactive",
                  "critical": true,
                  "descriptionKey": "pool.sos.description"
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(Pool.serializer(), v1Json)

        assertEquals(1, decoded.schemaVersion)
        assertEquals(2, decoded.declarations.size)

        val font = decoded.byId("font-tile")!!
        assertNull(font.requires, "v1 pool must default requires to null")
        assertEquals(false, font.required, "v1 pool must default required to false")

        val sos = decoded.byId("sos-main")!!
        assertNull(sos.requires)
        assertEquals(false, sos.required)
    }
}
