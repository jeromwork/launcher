package com.launcher.api.wizard

import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.ConfigDocumentHeader
import com.launcher.api.wizard.data.ConfigParser
import com.launcher.api.wizard.data.SystemSettingEntry
import com.launcher.api.wizard.data.SystemSettingsPoolBody
import com.launcher.api.wizard.data.WireCriticality
import com.launcher.api.wizard.data.WireDetectionStrategy
import com.launcher.api.wizard.data.WireSettingMechanism
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Pool schemaVersion 2 roundtrip per contracts/system-settings-pool-v2.md §7.
 * Full v2 pool document with all 6 entries containing check/apply blocks.
 */
class PoolSchemaV2RoundtripTest {

    @Test
    fun poolV2_fullRoundtrip_allSixEntries() {
        val original = ConfigDocument.SystemSettingsPoolDoc(
            header = ConfigDocumentHeader(
                schemaVersion = 2,
                id = "system-settings.android-pool",
                name = "system_settings_android_pool.name",
                description = "system_settings_android_pool.desc",
                deviceClass = listOf("android-phone"),
            ),
            body = SystemSettingsPoolBody(
                platform = "android",
                settings = listOf(
                    SystemSettingEntry(
                        id = "android.role.home",
                        mechanism = WireSettingMechanism.DeepLink,
                        criticality = WireCriticality.Required,
                        canSkip = true,
                        deepLink = null,
                        androidMinApi = 29,
                        dependsOn = emptyList(),
                        detectionStrategy = WireDetectionStrategy.Programmatic,
                        labelKey = "system_setting_role_home_label",
                        descriptionKey = "system_setting_role_home_desc",
                        extendedInstructionKey = "system_setting_role_home_retry_message",
                        check = CheckSpec.AndroidPackageHome(),
                        apply = ApplySpec.AndroidRoleRequest(role = "HOME"),
                    ),
                    SystemSettingEntry(
                        id = "android.permission.POST_NOTIFICATIONS",
                        mechanism = WireSettingMechanism.StandardPermission,
                        criticality = WireCriticality.Required,
                        canSkip = false,
                        androidMinApi = 33,
                        detectionStrategy = WireDetectionStrategy.Programmatic,
                        labelKey = "system_setting_post_notifications_label",
                        descriptionKey = "system_setting_post_notifications_desc",
                        check = CheckSpec.AndroidPermission(permission = "android.permission.POST_NOTIFICATIONS"),
                        apply = ApplySpec.StandardPermissionRequest(permission = "android.permission.POST_NOTIFICATIONS"),
                    ),
                    SystemSettingEntry(
                        id = "android.permission.CALL_PHONE",
                        mechanism = WireSettingMechanism.StandardPermission,
                        criticality = WireCriticality.Optional,
                        canSkip = true,
                        detectionStrategy = WireDetectionStrategy.Programmatic,
                        labelKey = "system_setting_call_phone_label",
                        descriptionKey = "system_setting_call_phone_desc",
                        check = CheckSpec.AndroidPermission(permission = "android.permission.CALL_PHONE"),
                        apply = ApplySpec.StandardPermissionRequest(permission = "android.permission.CALL_PHONE"),
                    ),
                    SystemSettingEntry(
                        id = "android.accessibility.our-service",
                        mechanism = WireSettingMechanism.AccessibilityService,
                        criticality = WireCriticality.Optional,
                        canSkip = true,
                        detectionStrategy = WireDetectionStrategy.Programmatic,
                        labelKey = "system_setting_accessibility_label",
                        descriptionKey = "system_setting_accessibility_desc",
                        check = CheckSpec.AndroidAccessibilityService(),
                        apply = ApplySpec.SettingsDeepLink(action = "android.settings.ACCESSIBILITY_SETTINGS"),
                    ),
                    SystemSettingEntry(
                        id = "android.battery.ignore_optimizations",
                        mechanism = WireSettingMechanism.SpecialPermission,
                        criticality = WireCriticality.Optional,
                        canSkip = true,
                        androidMinApi = 23,
                        detectionStrategy = WireDetectionStrategy.Programmatic,
                        labelKey = "system_setting_battery_label",
                        descriptionKey = "system_setting_battery_desc",
                        check = CheckSpec.AndroidSpecialPermission(variant = "ignore_battery_optimizations"),
                        apply = ApplySpec.SettingsDeepLink(
                            action = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                            packageScoped = true,
                        ),
                    ),
                    SystemSettingEntry(
                        id = "android.hide_status_bar",
                        mechanism = WireSettingMechanism.AccessibilityService,
                        criticality = WireCriticality.Optional,
                        canSkip = true,
                        detectionStrategy = WireDetectionStrategy.Indeterminate,
                        labelKey = "system_setting_hide_status_bar_label",
                        descriptionKey = "system_setting_hide_status_bar_desc",
                        check = CheckSpec.AndroidAccessibilityService(),
                        apply = ApplySpec.InAppOnly,
                    ),
                ),
            ),
        )

        val encoded = ConfigParser.encode(original)
        val result = ConfigParser.parse(ConfigKind.SystemSettingsPool, encoded)
        assertIs<ConfigSourceResult.Success>(result)
        assertEquals(original, result.document)
    }
}
