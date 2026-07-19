package com.launcher.preset.adapter

import android.os.Build
import com.launcher.preset.model.Vendor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

/**
 * TASK-73 (FR-001, Clarifications #2, plan.md §7). `Build.MANUFACTURER` stub
 * via Robolectric's [ReflectionHelpers] (same pattern already used for
 * `Build.VERSION.SDK_INT` elsewhere in this codebase).
 */
@RunWith(RobolectricTestRunner::class)
class AndroidVendorDetectorTest {

    private fun detectFor(manufacturer: String): Vendor {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", manufacturer)
        return AndroidVendorDetector().detect()
    }

    @Test
    fun knownManufacturer_mapsToMatchingVendorEnumValue() {
        assertEquals(Vendor.Xiaomi, detectFor("Xiaomi"))
        assertEquals(Vendor.Samsung, detectFor("Samsung"))
        assertEquals(Vendor.Huawei, detectFor("Huawei"))
    }

    @Test
    fun redmiOrPoco_anyCase_mapsToXiaomi() {
        assertEquals(Vendor.Xiaomi, detectFor("Redmi"))
        assertEquals(Vendor.Xiaomi, detectFor("REDMI"))
        assertEquals(Vendor.Xiaomi, detectFor("poco"))
        assertEquals(Vendor.Xiaomi, detectFor("POCO"))
    }

    @Test
    fun unrecognizedManufacturer_mapsToGenericAndroid() {
        assertEquals(Vendor.GenericAndroid, detectFor("SomeObscureOEM"))
    }
}
