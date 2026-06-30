package com.launcher.api.preset

import com.launcher.api.switchstrategy.CopyOnActivateStrategy
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.ConfigParser
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec T66G (FR-025, SC-010) — composition snapshot regression.
 *
 * Loads `simple-launcher.preset.json` from bundled assets and derives the
 * initial ProfileData via the production CopyOnActivateStrategy. The
 * resulting ProfileData is serialized to JSON and compared against a golden
 * fixture. First run creates the golden file; subsequent runs assert
 * byte-equality (any drift in preset wire format or strategy is caught).
 *
 * To intentionally regenerate the golden: delete the file and rerun.
 *
 * Note: T66G in tasks.md originally asked for a "wizard derived from
 * preset" snapshot. The composition engine is not part of TASK-65 (deferred
 * to a follow-up task). Until then, this test guards the same surface:
 * preset wire format -> default ProfileData transformation.
 */
class SimpleLauncherCompositionRegressionTest {

    private val presetFile = File("src/androidMain/assets/presets/simple-launcher.preset.json")
        .takeIf { it.exists() }
        ?: File("../core/src/androidMain/assets/presets/simple-launcher.preset.json")

    private val goldenDir = File("src/androidUnitTest/resources/fixtures").also { it.mkdirs() }
    private val goldenFile = File(goldenDir, "simple-launcher-profile-golden.json")

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun bundledPresetParsesAndComposesIntoStableProfileData() {
        assertTrue("preset file present at ${presetFile.absolutePath}", presetFile.exists())

        val raw = presetFile.readText()
        val result = ConfigParser.parse(ConfigKind.Preset, raw)
        val doc = (result as ConfigSourceResult.Success).document as ConfigDocument.PresetDoc
        val preset = doc.preset

        val profile = CopyOnActivateStrategy().migrate(from = null, toPreset = preset)
        val actualJson = json.encodeToString(
            com.launcher.api.profile.ProfileData.serializer(),
            profile,
        )

        if (!goldenFile.exists()) {
            goldenFile.parentFile.mkdirs()
            goldenFile.writeText(actualJson)
            // First run: create golden; subsequent runs assert equality.
            return
        }
        val expected = goldenFile.readText().trim()
        assertEquals(
            "ProfileData drift vs golden ${goldenFile.absolutePath} — " +
                "delete the golden file and rerun if intentional.",
            expected,
            actualJson.trim(),
        )
    }
}
