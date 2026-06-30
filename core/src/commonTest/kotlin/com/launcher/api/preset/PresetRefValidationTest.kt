package com.launcher.api.preset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PresetRefValidationTest {

    @Test
    fun rejectsBlankUid() {
        assertFailsWith<IllegalArgumentException> { PresetRef(uid = "", version = 1) }
        assertFailsWith<IllegalArgumentException> { PresetRef(uid = "  ", version = 1) }
    }

    @Test
    fun rejectsUidContainingSeparator() {
        assertFailsWith<IllegalArgumentException> {
            PresetRef(uid = "com.launcher::evil", version = 1)
        }
    }

    @Test
    fun rejectsVersionBelowOne() {
        assertFailsWith<IllegalArgumentException> { PresetRef(uid = "ok", version = 0) }
        assertFailsWith<IllegalArgumentException> { PresetRef(uid = "ok", version = -3) }
    }

    @Test
    fun compositeKeyRoundtripsExactly() {
        val ref = PresetRef(uid = "com.launcher.preset.simple-launcher", version = 7)
        val key = ref.toCompositeKey()
        assertEquals("com.launcher.preset.simple-launcher::7", key)
        assertEquals(ref, PresetRef.parseCompositeKey(key))
    }

    @Test
    fun parseRejectsMissingSeparator() {
        assertFailsWith<IllegalArgumentException> {
            PresetRef.parseCompositeKey("no-separator-here")
        }
    }

    @Test
    fun parseRejectsNonNumericVersion() {
        assertFailsWith<IllegalStateException> {
            PresetRef.parseCompositeKey("com.launcher.preset.simple-launcher::abc")
        }
    }
}
