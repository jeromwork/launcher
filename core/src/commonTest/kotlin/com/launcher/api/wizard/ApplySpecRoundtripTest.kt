package com.launcher.api.wizard

import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.ConfigParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Wire-format roundtrip test for [ApplySpec] per CLAUDE.md rule 5 and
 * contracts/system-settings-pool-v2.md §7.
 */
class ApplySpecRoundtripTest {

    private val json = ConfigParser.json

    @Test
    fun standardPermissionRequest_roundtrip() {
        val original: ApplySpec = ApplySpec.StandardPermissionRequest(
            permission = "android.permission.POST_NOTIFICATIONS",
        )
        val encoded = json.encodeToString(ApplySpec.serializer(), original)
        val decoded = json.decodeFromString(ApplySpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun androidRoleRequest_roundtrip() {
        val original: ApplySpec = ApplySpec.AndroidRoleRequest(role = "HOME")
        val encoded = json.encodeToString(ApplySpec.serializer(), original)
        val decoded = json.decodeFromString(ApplySpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun settingsDeepLink_roundtrip_packageScoped() {
        val original: ApplySpec = ApplySpec.SettingsDeepLink(
            action = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            packageScoped = true,
        )
        val encoded = json.encodeToString(ApplySpec.serializer(), original)
        val decoded = json.decodeFromString(ApplySpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun settingsDeepLink_roundtrip_notScoped() {
        val original: ApplySpec = ApplySpec.SettingsDeepLink(
            action = "android.settings.ACCESSIBILITY_SETTINGS",
            packageScoped = false,
        )
        val encoded = json.encodeToString(ApplySpec.serializer(), original)
        val decoded = json.decodeFromString(ApplySpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun inAppOnly_roundtrip() {
        val original: ApplySpec = ApplySpec.InAppOnly
        val encoded = json.encodeToString(ApplySpec.serializer(), original)
        val decoded = json.decodeFromString(ApplySpec.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
