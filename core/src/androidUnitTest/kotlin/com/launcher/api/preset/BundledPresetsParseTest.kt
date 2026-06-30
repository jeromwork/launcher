package com.launcher.api.preset

import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.ConfigParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build-time validation that every bundled `*.preset.json` parses and that
 * PresetRef identities don't collide. JVM unit test reads from the source
 * tree directly — bundled assets live at
 * `core/src/androidMain/assets/presets/` (no AVD required).
 */
class BundledPresetsParseTest {

    private val presetsDir: File = File("src/androidMain/assets/presets").let {
        if (it.exists()) it else File("../core/src/androidMain/assets/presets")
    }

    @Test
    fun directoryExistsAndContainsBundledPresets() {
        assertTrue("presets dir found at ${presetsDir.absolutePath}", presetsDir.isDirectory)
        val files = presetsDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
        assertTrue("expected ≥1 bundled preset, got ${files.size}", files.isNotEmpty())
    }

    @Test
    fun everyBundledPresetParsesAndHasUniqueRef() {
        val files = presetsDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
        val refs = mutableSetOf<PresetRef>()
        for (f in files) {
            val raw = f.readText()
            val result = ConfigParser.parse(ConfigKind.Preset, raw)
            assertTrue(
                "preset ${f.name} failed to parse: $result",
                result is ConfigSourceResult.Success,
            )
            val doc = (result as ConfigSourceResult.Success).document as ConfigDocument.PresetDoc
            assertNotNull(doc.preset.uid)
            assertEquals(PRESET_SCHEMA_VERSION, doc.preset.schemaVersion)
            val ref = doc.preset.ref
            assertTrue(
                "duplicate PresetRef across bundled presets: $ref",
                refs.add(ref),
            )
        }
    }
}
