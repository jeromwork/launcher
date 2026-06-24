package com.launcher.architecture

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * TASK-7 architecture fitness functions (CLAUDE.md rule 7 — fitness over
 * manual review for machine-judgeable invariants).
 *
 * Initial gate (T7-004 only at Phase 0): CheckSpec.kt and ApplySpec.kt do
 * NOT import Android types — they remain pure commonMain so future iOS /
 * Android-TV adapter modules add their own variants without touching the
 * sealed hierarchy (Article VII §15 multi-platform seam).
 *
 * Implemented as a string scan (matches the [com.launcher.test.fitness.DomainIsolationTest]
 * pattern) rather than Konsist because androidUnitTest's user.dir flips
 * between the project root and :core/ depending on the runner.
 *
 * Additional gates T7-001..T7-006 are added in Phase 7 (T059).
 */
class Task7ArchitectureTest {

    private val commonMainDir: File by lazy {
        // Resolve regardless of working dir (project root vs :core/)
        val candidates = listOf(
            File("core/src/commonMain/kotlin/com/launcher/api/wizard/data"),
            File("src/commonMain/kotlin/com/launcher/api/wizard/data"),
            File("../core/src/commonMain/kotlin/com/launcher/api/wizard/data"),
        )
        candidates.firstOrNull { it.exists() }
            ?: error("commonMain data dir not found from ${File(".").absolutePath}")
    }

    @Test
    fun t7_004_checkSpec_hasNoAndroidImports() {
        val file = File(commonMainDir, "CheckSpec.kt")
        assertTrue("CheckSpec.kt exists at ${file.absolutePath}", file.exists())
        val text = file.readText()
        val forbiddenImports = forbiddenAndroidImports(text)
        assertFalse(
            "CheckSpec.kt MUST NOT import Android / vendor types; found: $forbiddenImports",
            forbiddenImports.isNotEmpty(),
        )
    }

    @Test
    fun t7_004_applySpec_hasNoAndroidImports() {
        val file = File(commonMainDir, "ApplySpec.kt")
        assertTrue("ApplySpec.kt exists at ${file.absolutePath}", file.exists())
        val text = file.readText()
        val forbiddenImports = forbiddenAndroidImports(text)
        assertFalse(
            "ApplySpec.kt MUST NOT import Android / vendor types; found: $forbiddenImports",
            forbiddenImports.isNotEmpty(),
        )
    }

    private fun forbiddenAndroidImports(source: String): List<String> {
        val forbiddenPrefixes = listOf(
            "android.",
            "androidx.activity.",
            "androidx.appcompat.",
            "androidx.core.",
            "androidx.lifecycle.",
            "androidx.datastore.",
            "com.google.firebase.",
            "com.google.android.",
        )
        return source.lines()
            .map { it.trim() }
            .filter { it.startsWith("import ") }
            .mapNotNull { line ->
                val pkg = line.removePrefix("import ").substringBefore(' ')
                forbiddenPrefixes.firstOrNull { pkg.startsWith(it) }?.let { _ -> pkg }
            }
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
