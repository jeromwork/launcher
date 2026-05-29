package com.launcher.api.edit

import com.launcher.api.result.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wire-format tests for [NamedConfig] envelope per
 * [contracts/named-config-local.md](../../../../specs/014-tile-editing-admin-senior-profiles/contracts/named-config-local.md)
 * v1.
 *
 * Covers:
 *  - T040: roundtrip serialize → deserialize → assertEquals.
 *  - T041: schemaVersion fail-closed forward-compat (CHK008 wire-format).
 *  - T042: defaults applied for missing optional fields (CHK005).
 *
 * Fixtures inlined as strings per project's KMP commonTest convention (see
 * ConfigDocumentWireFormatTest для reference). Sample files в
 * `commonTest/resources/fixtures/spec014/` reference canonical shape.
 */
class NamedConfigWireFormatTest {

    private val device1 = "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a"
    private val device2 = "1f8e3a5e-2e7c-5f7b-aa1d-3b3c4d5e6f7b"

    // ─── T040 — roundtrip ─────────────────────────────────────────────────

    @Test
    fun T040_roundtrip_single_default_config() {
        val envelope = NamedConfigWireFormat.Envelope(
            configs = listOf(
                NamedConfig(
                    configName = "default",
                    isDefault = true,
                    presetId = "workspace",
                    deviceClass = "phone",
                    activeDeviceIds = setOf(device1),
                ),
            ),
        )

        val wire = NamedConfigWireFormat.serialize(envelope)
        val parsed = NamedConfigWireFormat.deserialize(wire).orFail()

        assertEquals(envelope, parsed)
    }

    @Test
    fun T040_roundtrip_three_mix_configs() {
        val envelope = NamedConfigWireFormat.Envelope(
            configs = listOf(
                NamedConfig(
                    configName = "default",
                    isDefault = true,
                    presetId = "workspace",
                    deviceClass = "phone",
                    activeDeviceIds = setOf(device1),
                ),
                NamedConfig(
                    configName = "job",
                    description = "Workspace для работы",
                    isDefault = false,
                    presetId = "workspace",
                    deviceClass = "phone",
                    activeDeviceIds = setOf(device2),
                ),
                NamedConfig(
                    configName = "old",
                    isDefault = false,
                    presetId = "simple-launcher",
                    deviceClass = "phone",
                    activeDeviceIds = emptySet(),
                    orphanedAt = 1_700_000_000_000L,
                ),
            ),
        )

        val wire = NamedConfigWireFormat.serialize(envelope)
        val parsed = NamedConfigWireFormat.deserialize(wire).orFail()

        assertEquals(envelope, parsed)
    }

    @Test
    fun T040_roundtrip_preserves_cyrillic_config_name() {
        val envelope = NamedConfigWireFormat.Envelope(
            configs = listOf(
                NamedConfig(
                    configName = "Дом",
                    description = "Домашний",
                    isDefault = true,
                    presetId = "workspace",
                    deviceClass = "phone",
                    activeDeviceIds = setOf(device1),
                ),
            ),
        )

        val wire = NamedConfigWireFormat.serialize(envelope)
        val parsed = NamedConfigWireFormat.deserialize(wire).orFail()

        assertEquals(envelope, parsed)
        assertEquals("Дом", parsed.configs[0].configName)
    }

    // ─── T041 — fail-closed forward-compat ────────────────────────────────

    @Test
    fun T041_envelope_with_future_schema_version_returns_UnsupportedSchemaVersion() {
        val futureVersion = """
            {
              "schemaVersion": 99,
              "configs": []
            }
        """.trimIndent()

        val result = NamedConfigWireFormat.deserialize(futureVersion)

        assertTrue(result is Outcome.Failure, "expected Failure, got: $result")
        val err = result.error
        assertTrue(err is StoreError.UnsupportedSchemaVersion, "expected UnsupportedSchemaVersion, got: $err")
        assertEquals(99, err.found)
        assertEquals(NamedConfig.CURRENT_SCHEMA_VERSION, err.supported)
    }

    @Test
    fun T041_inner_config_with_future_schema_version_returns_UnsupportedSchemaVersion() {
        // Container is v1, но inner config claims v2 — also fail-closed.
        val futureInner = """
            {
              "schemaVersion": 1,
              "configs": [
                {
                  "schemaVersion": 2,
                  "configName": "default",
                  "isDefault": true,
                  "presetId": "workspace",
                  "deviceClass": "phone",
                  "activeDeviceIds": ["$device1"]
                }
              ]
            }
        """.trimIndent()

        val result = NamedConfigWireFormat.deserialize(futureInner)

        assertTrue(result is Outcome.Failure, "expected Failure for inner v2: $result")
        val err = result.error
        assertTrue(err is StoreError.UnsupportedSchemaVersion)
        assertEquals(2, err.found)
    }

    @Test
    fun T041_malformed_json_returns_UnsupportedSchemaVersion_with_minus_one() {
        val malformed = "this is not json {"

        val result = NamedConfigWireFormat.deserialize(malformed)

        assertTrue(result is Outcome.Failure)
        val err = result.error
        assertTrue(err is StoreError.UnsupportedSchemaVersion)
        assertEquals(-1, err.found)
    }

    // ─── T042 — defaults for missing optional fields ──────────────────────

    @Test
    fun T042_missing_description_defaults_to_empty_string() {
        val minimal = """
            {
              "schemaVersion": 1,
              "configs": [
                {
                  "schemaVersion": 1,
                  "configName": "default",
                  "isDefault": true,
                  "presetId": "workspace",
                  "deviceClass": "phone",
                  "activeDeviceIds": ["$device1"]
                }
              ]
            }
        """.trimIndent()

        val parsed = NamedConfigWireFormat.deserialize(minimal).orFail()

        assertEquals("", parsed.configs[0].description)
    }

    @Test
    fun T042_missing_orphanedAt_defaults_to_null() {
        val minimal = """
            {
              "schemaVersion": 1,
              "configs": [
                {
                  "schemaVersion": 1,
                  "configName": "default",
                  "isDefault": true,
                  "presetId": "workspace",
                  "deviceClass": "phone",
                  "activeDeviceIds": ["$device1"]
                }
              ]
            }
        """.trimIndent()

        val parsed = NamedConfigWireFormat.deserialize(minimal).orFail()

        assertEquals(null, parsed.configs[0].orphanedAt)
    }

    @Test
    fun T042_missing_envelope_schemaVersion_defaults_to_v1() {
        // Per project convention WireFormatJson.json `ignoreUnknownKeys = true` —
        // missing schemaVersion field is interpreted as v1 (additive forward-compat).
        val noSchemaVersion = """
            {
              "configs": []
            }
        """.trimIndent()

        val result = NamedConfigWireFormat.deserialize(noSchemaVersion)

        assertTrue(result is Outcome.Success, "expected Success when schemaVersion missing: $result")
        assertEquals(NamedConfig.CURRENT_SCHEMA_VERSION, result.value.schemaVersion)
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun <T> Outcome<T, StoreError>.orFail(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("expected Success, got Failure($error)")
    }
}
