package com.launcher.preset.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T025 (FR-007 CL-7, contracts/hint-pool-schema-v1.md, CLAUDE.md rule 5).
 *
 * `hint-pool.json` catalog format:
 * ```json
 * { "schemaVersion": 1, "hints": [ { "hintId": "...", "titleKey": "...", "bodyKey": "..." } ] }
 * ```
 *
 * The runtime domain model for hints (as consumed by [com.launcher.preset.port.HintPoolSource])
 * uses [com.launcher.preset.model.HintFlowEntry] with `targetComponentId` + `textKey` — that is
 * the *join* shape produced at load time. This test guards the raw *catalog* wire format that
 * lives on disk / in bundled assets: fields `hintId`, `titleKey`, `bodyKey` and top-level
 * `schemaVersion`.
 *
 * The model classes below are test-scoped mirrors of the wire shape declared in
 * `contracts/hint-pool-schema-v1.md`. The bundled adapter (T035) will introduce its own
 * production model.
 */
class HintPoolSchemaV1RoundtripTest {

    @Serializable
    private data class HintDescriptor(
        val hintId: String,
        val titleKey: String,
        val bodyKey: String,
    )

    @Serializable
    private data class HintPool(
        val schemaVersion: Int,
        val hints: List<HintDescriptor> = emptyList(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun v1HintPool_roundtrips() {
        val pool = HintPool(
            schemaVersion = 1,
            hints = listOf(
                HintDescriptor("hint-launcher-role", "hint_launcher_role_title", "hint_launcher_role_body"),
                HintDescriptor("hint-status-bar", "hint_status_bar_title", "hint_status_bar_body"),
            ),
        )

        val encoded = json.encodeToString(HintPool.serializer(), pool)
        val decoded = json.decodeFromString(HintPool.serializer(), encoded)

        assertEquals(pool, decoded)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(2, decoded.hints.size)

        // Bit-identical re-encode.
        assertEquals(encoded, json.encodeToString(HintPool.serializer(), decoded))
    }

    @Test
    fun emptyHintPool_roundtrips() {
        val v1Json = """{"schemaVersion":1,"hints":[]}"""
        val decoded = json.decodeFromString(HintPool.serializer(), v1Json)
        assertEquals(1, decoded.schemaVersion)
        assertTrue(decoded.hints.isEmpty())
    }

    @Test
    fun bundledFormatHardcoded_decodes() {
        // Sanity: exact shape from contracts/hint-pool-schema-v1.md example.
        val bundled = """
            {
              "schemaVersion": 1,
              "hints": [
                {
                  "hintId": "hint-launcher-role",
                  "titleKey": "hint_launcher_role_title",
                  "bodyKey": "hint_launcher_role_body"
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(HintPool.serializer(), bundled)
        assertEquals(1, decoded.schemaVersion)
        assertEquals("hint-launcher-role", decoded.hints.single().hintId)
    }
}
