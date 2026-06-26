package com.launcher.api.wizard

import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.data.ConfigParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Wire-format roundtrip test for [CheckSpec] per CLAUDE.md rule 5 and
 * contracts/system-settings-pool-v2.md §7.
 *
 * For each of the 5 variants: serialize → deserialize → assertEquals.
 */
class CheckSpecRoundtripTest {

    private val json = ConfigParser.json

    @Test
    fun androidRole_roundtrip() {
        val original: CheckSpec = CheckSpec.AndroidRole(role = "HOME")
        val encoded = json.encodeToString(CheckSpec.serializer(), original)
        val decoded = json.decodeFromString(CheckSpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun androidPermission_roundtrip() {
        val original: CheckSpec = CheckSpec.AndroidPermission(permission = "android.permission.POST_NOTIFICATIONS")
        val encoded = json.encodeToString(CheckSpec.serializer(), original)
        val decoded = json.decodeFromString(CheckSpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun androidSpecialPermission_roundtrip() {
        val original: CheckSpec = CheckSpec.AndroidSpecialPermission(variant = "ignore_battery_optimizations")
        val encoded = json.encodeToString(CheckSpec.serializer(), original)
        val decoded = json.decodeFromString(CheckSpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun androidAccessibilityService_roundtrip_withComponent() {
        val original: CheckSpec = CheckSpec.AndroidAccessibilityService(componentName = "com.launcher.app/.SvcA")
        val encoded = json.encodeToString(CheckSpec.serializer(), original)
        val decoded = json.decodeFromString(CheckSpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun androidAccessibilityService_roundtrip_nullComponent() {
        val original: CheckSpec = CheckSpec.AndroidAccessibilityService(componentName = null)
        val encoded = json.encodeToString(CheckSpec.serializer(), original)
        val decoded = json.decodeFromString(CheckSpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun androidPackageHome_roundtrip_withPackage() {
        val original: CheckSpec = CheckSpec.AndroidPackageHome(packageName = "com.launcher.app")
        val encoded = json.encodeToString(CheckSpec.serializer(), original)
        val decoded = json.decodeFromString(CheckSpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun androidPackageHome_roundtrip_nullPackage() {
        val original: CheckSpec = CheckSpec.AndroidPackageHome(packageName = null)
        val encoded = json.encodeToString(CheckSpec.serializer(), original)
        val decoded = json.decodeFromString(CheckSpec.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun discriminatorField_is_kind() {
        val original: CheckSpec = CheckSpec.AndroidRole(role = "HOME")
        val encoded = json.encodeToString(CheckSpec.serializer(), original)
        // Wire format must use "kind" as discriminator.
        assertEquals(true, encoded.contains("\"kind\":\"android-role\""))
    }
}
