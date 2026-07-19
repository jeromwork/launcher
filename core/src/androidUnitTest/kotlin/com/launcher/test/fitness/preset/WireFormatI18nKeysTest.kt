package com.launcher.test.fitness.preset

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-120 fitness #10 — user-facing wire-format string fields carry i18n
 * keys, not literal text. Scans bundled pool.json + presets for values in
 * `labelKey`, `descriptionKey`, `wizardTitleKey`, `wizardIntroKey`,
 * `categoryKey`, `layoutKey`, `iconKey` and asserts they look like
 * dotted i18n keys (`domain.area.field`), not a human sentence.
 */
class WireFormatI18nKeysTest {

    private val keyFields = listOf(
        "labelKey", "descriptionKey", "wizardTitleKey", "wizardIntroKey",
        "categoryKey", "layoutKey", "iconKey", "messageKey",
        // TASK-73 — vendor-recipes.json VendorOverride.fallbackTextKey.
        "fallbackTextKey",
    )

    // Literal-text heuristic: any value that is NOT a lowercase.dotted.identifier
    // is considered a literal string leak.
    private val i18nKeyShape = Regex("""^[a-z0-9]+(?:[._\-][a-z0-9]+)+$""")

    private val fieldPattern by lazy {
        val names = keyFields.joinToString("|")
        Regex("""\"($names)\"\s*:\s*\"([^\"]+)\"""")
    }

    @Test
    fun bundledAssets_useI18nKeys_notLiteralStrings() {
        val assetsDir = locateAssetsDir() ?: return
        val jsons = assetsDir.walkTopDown().filter { it.isFile && it.extension == "json" }.toList()
        val violations = mutableListOf<String>()
        for (file in jsons) {
            fieldPattern.findAll(file.readText()).forEach { match ->
                val field = match.groupValues[1]
                val value = match.groupValues[2]
                if (!i18nKeyShape.matches(value)) {
                    violations += "${file.path}: $field=\"$value\""
                }
            }
        }
        assertTrue(
            "Fitness #10: user-facing wire-format fields must carry i18n keys, not literals.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun locateAssetsDir(): File? {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "../app/src/main/assets/preset"),
            File(cwd, "app/src/main/assets/preset"),
        )
        return candidates.firstOrNull { it.isDirectory }
    }
}
