package com.launcher.api.wizard.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WizardManifestBackwardCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun migrateLegacyV1RemovesAppFamilyIdAndBumpsSchemaVersion() {
        val legacy = """
            {
              "schemaVersion": 1,
              "id": "wizard-manifest.simple-launcher",
              "name": "n",
              "description": "d",
              "deviceClass": ["android-phone"],
              "body": {
                "appFamilyId": "simple-launcher",
                "autoOrder": false,
                "steps": []
              }
            }
        """.trimIndent()

        val root = json.parseToJsonElement(legacy).jsonObject
        val migrated = migrateLegacyWizardManifest(root)

        assertEquals(2, migrated["schemaVersion"]?.jsonPrimitive?.intOrNull)
        val body = migrated["body"]?.jsonObject
        assertNotNull(body)
        assertNull(body["appFamilyId"], "appFamilyId must be removed from body")
        assertEquals(false, body["autoOrder"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun migrateIsIdempotentOnV2Input() {
        val v2 = """
            {
              "schemaVersion": 2,
              "id": "x",
              "name": "n",
              "description": "d",
              "deviceClass": [],
              "body": { "autoOrder": true }
            }
        """.trimIndent()
        val root = json.parseToJsonElement(v2).jsonObject
        val migrated = migrateLegacyWizardManifest(root)
        assertEquals(root, migrated)
    }

    @Test
    fun parserAcceptsLegacyV1Manifest() {
        val legacy = """
            {
              "schemaVersion": 1,
              "id": "wizard-manifest.simple-launcher",
              "name": "n",
              "description": "d",
              "deviceClass": ["android-phone"],
              "body": {
                "appFamilyId": "simple-launcher",
                "autoOrder": false
              }
            }
        """.trimIndent()
        val result = ConfigParser.parse(
            com.launcher.api.wizard.ConfigKind.WizardManifest,
            legacy,
        )
        val success = result as com.launcher.api.wizard.ConfigSourceResult.Success
        val manifest = success.document as ConfigDocument.Manifest
        // Header schemaVersion bumped to 2 by migration.
        assertEquals(2, manifest.header.schemaVersion)
        // appFamilyId removed → null in body.
        assertEquals(null, manifest.body.appFamilyId)
        assertFalse(manifest.body.autoOrder)
    }
}
