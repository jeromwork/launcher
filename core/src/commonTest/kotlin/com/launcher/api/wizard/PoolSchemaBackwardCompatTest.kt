package com.launcher.api.wizard

import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.ConfigParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Backward-compat read: a v1 pool fixture (no `check`/`apply` blocks)
 * deserialized via the v2 reader yields `check == null && apply == null`.
 *
 * Per contracts/system-settings-pool-v2.md §7 + §8 — v2 readers MUST be
 * able to read v1 documents until v3 removes the legacy fields.
 */
class PoolSchemaBackwardCompatTest {

    private val v1Fixture = """
        {
          "schemaVersion": 1,
          "id": "system-settings.android-pool",
          "name": "system_settings_android_pool.name",
          "description": "system_settings_android_pool.desc",
          "deviceClass": ["android-phone"],
          "body": {
            "platform": "android",
            "settings": [
              {
                "id": "android.role.home",
                "mechanism": "DeepLink",
                "criticality": "Required",
                "canSkip": true,
                "deepLink": "RoleManager.createRequestRoleIntent(ROLE_HOME)",
                "androidMinApi": 29,
                "dependsOn": [],
                "detectionStrategy": "Programmatic",
                "labelKey": "system_setting_role_home_label",
                "descriptionKey": "system_setting_role_home_desc"
              },
              {
                "id": "android.permission.POST_NOTIFICATIONS",
                "mechanism": "StandardPermission",
                "criticality": "Required",
                "canSkip": false,
                "androidMinApi": 33,
                "dependsOn": [],
                "detectionStrategy": "Programmatic",
                "labelKey": "system_setting_post_notifications_label",
                "descriptionKey": "system_setting_post_notifications_desc"
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun v1Pool_readViaV2Reader_yieldsNullCheckAndApply() {
        val result = ConfigParser.parse(ConfigKind.SystemSettingsPool, v1Fixture)
        assertIs<ConfigSourceResult.Success>(result)
        val doc = result.document
        assertIs<ConfigDocument.SystemSettingsPoolDoc>(doc)
        assertEquals(1, doc.header.schemaVersion)
        assertEquals(2, doc.body.settings.size)
        for (entry in doc.body.settings) {
            assertNull(entry.check, "v1 entry ${entry.id} must have check=null")
            assertNull(entry.apply, "v1 entry ${entry.id} must have apply=null")
        }
    }

    @Test
    fun v1Pool_preservesLegacyFields() {
        val result = ConfigParser.parse(ConfigKind.SystemSettingsPool, v1Fixture)
        assertIs<ConfigSourceResult.Success>(result)
        val doc = result.document as ConfigDocument.SystemSettingsPoolDoc
        val roleHome = doc.body.settings.first { it.id == "android.role.home" }
        assertEquals("RoleManager.createRequestRoleIntent(ROLE_HOME)", roleHome.deepLink)
        assertEquals(29, roleHome.androidMinApi)
        assertEquals("system_setting_role_home_label", roleHome.labelKey)
    }
}
