package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-51 T070 — fitness rule: zero `com.goterl.*` imports anywhere in
 * production sources. Phase 7 deleted the legacy lazysodium stack;
 * this rule prevents accidental reintroduction (FR-007, SC-003, SC-005).
 *
 * Whitelist:
 *  - fitness tests (this and Spec011IsolationTest reference the banned
 *    string literal as the forbidden pattern itself).
 *  - test source sets (`/src/test/`, `/src/androidUnitTest/`,
 *    `/src/commonTest/`, `/src/androidInstrumentedTest/`).
 *  - specs/ + docs/ — historical artefacts, not built into production.
 */
class NoLazysodiumInProductionTest {

    @Test
    fun productionSources_doNotImport_comGoterl() {
        val violations = scanProductionForImports("com.goterl")
        assertTrue(
            "Lazysodium (com.goterl.*) removed in TASK-51 Phase 7. " +
                "Re-introduction blocked (FR-007, SC-003, SC-005).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun scanProductionForImports(forbiddenPrefix: String): List<String> {
        val repoRoot = locateRepoRoot()
        val violations = mutableListOf<String>()
        repoRoot.walkTopDown()
            .onEnter { dir -> !isExcludedDir(dir) }
            .filter { it.isFile && it.extension == "kt" }
            .filter { !isTestSource(it) && !isFitnessFile(it) }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { idx, raw ->
                        val line = raw.trim()
                        if (line.startsWith("import ") &&
                            line.removePrefix("import ").removeSuffix(";").trim()
                                .startsWith(forbiddenPrefix)
                        ) {
                            violations += "${file.path}:${idx + 1}: $line"
                        }
                    }
                }
            }
        return violations
    }

    private fun isExcludedDir(dir: File): Boolean {
        val name = dir.name
        return name == "build" || name == ".gradle" || name == ".git" ||
            name == "node_modules" || name == "specs" || name == "docs" ||
            name == "backlog"
    }

    private fun isTestSource(file: File): Boolean {
        val p = file.path.replace('\\', '/')
        return p.contains("/src/test/") ||
            p.contains("/src/androidUnitTest/") ||
            p.contains("/src/androidInstrumentedTest/") ||
            p.contains("/src/commonTest/") ||
            p.contains("/src/jvmTest/") ||
            p.contains("/src/androidRealBackendUnitTest/")
    }

    private fun isFitnessFile(file: File): Boolean =
        file.path.replace('\\', '/').contains("/test/fitness/")

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(5) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        // Fallback to cwd.
        return File(System.getProperty("user.dir"))
    }
}
