package com.launcher.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lightweight fitness rules for TASK-65 (FR-020, FR-021).
 *
 * Detekt-based equivalents (PresetIdBranchingDetector,
 * ExtractionReadinessDetector) are tracked in [tasks.md] T660-T665 + T66E/F
 * as the full version with Gradle module + AST analysis. This test gives us
 * the same behaviour today via grep on source files — runs in `:core`
 * unit tests, no extra setup. Replace with the Detekt module when it lands.
 */
class Task65FitnessTest {

    private val coreSrc = File("src")
    private val appSrc = File("../app/src/main")

    private val presetIdBranchPatterns = listOf(
        Regex("""if\s*\(\s*presetId\s*==\s*"[^"]+""""),
        Regex("""when\s*\(\s*presetId\s*\)"""),
        Regex("""if\s*\(\s*appFamilyId\s*==\s*"[^"]+""""),
        Regex("""when\s*\(\s*appFamilyId\s*\)"""),
    )

    private val extractionForbiddenImports = listOf(
        Regex("""^import\s+com\.launcher\.app\.tiles\b""", RegexOption.MULTILINE),
        Regex("""^import\s+com\.launcher\.app\.home\b""", RegexOption.MULTILINE),
        Regex("""^import\s+com\.launcher\.app\.contacts\b""", RegexOption.MULTILINE),
    )

    @Test
    fun fr020_noPresetIdBranchingOutsideWhitelist() {
        val whitelist = setOf(
            "com/launcher/api/preset",
            "com/launcher/core/preset/test",
            // Architecture tests legitimately reference the patterns as regex literals.
            "com/launcher/architecture",
        )
        val violations = mutableListOf<String>()
        for (root in listOf(coreSrc, appSrc).filter { it.exists() }) {
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val rel = f.absolutePath.replace('\\', '/')
                if (whitelist.any { rel.contains(it) }) return@forEach
                val text = f.readText()
                for (p in presetIdBranchPatterns) {
                    if (p.containsMatchIn(text)) {
                        violations += "$rel — matches ${p.pattern}"
                    }
                }
            }
        }
        assertTrue(
            "FR-020 violations (presetId / appFamilyId branching outside whitelist):\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun fr021_noAppLayerImportsInCorePresetWizardPools() {
        val scope = listOf(
            "com/launcher/api/preset",
            "com/launcher/api/profile",
            "com/launcher/api/pools",
            "com/launcher/api/switchstrategy",
            "com/launcher/api/wizard",
        )
        val violations = mutableListOf<String>()
        if (coreSrc.exists()) {
            coreSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val rel = f.absolutePath.replace('\\', '/')
                if (scope.none { rel.contains(it) }) return@forEach
                val text = f.readText()
                for (p in extractionForbiddenImports) {
                    p.findAll(text).forEach { m ->
                        violations += "$rel — forbidden import: ${m.value}"
                    }
                }
            }
        }
        assertEquals(
            "FR-021 violations (app-layer imports inside core preset/wizard/pools/switchstrategy):\n" +
                violations.joinToString("\n"),
            emptyList<String>(),
            violations,
        )
    }
}
