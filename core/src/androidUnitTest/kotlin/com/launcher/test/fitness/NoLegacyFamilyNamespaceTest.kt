package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-51 T072 + TASK-56 — fitness rule: zero `family.crypto.*` /
 * `family.pairing.*` / `family.keys.*` imports anywhere. All three
 * namespaces have been renamed to `cryptokit.*`:
 *  - `family.crypto.*`  -> `cryptokit.crypto.*` (TASK-51 Phase 4)
 *  - `family.pairing.*` -> `cryptokit.pairing.*` (TASK-51 Phase 4)
 *  - `family.keys.*`    -> `cryptokit.keys.*` (TASK-56)
 *
 * Whitelist: fitness tests + specs/ + docs/ + backlog/ (historical prose only).
 */
class NoLegacyFamilyNamespaceTest {

    @Test
    fun nothingImports_legacyFamilyNamespaces() {
        val violations = mutableListOf<String>()
        val forbiddenPrefixes = listOf("family.crypto", "family.pairing", "family.keys")
        scanRepo().forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { idx, raw ->
                    val line = raw.trim()
                    if (!line.startsWith("import ")) return@forEachIndexed
                    val target = line.removePrefix("import ").removeSuffix(";").trim()
                    if (forbiddenPrefixes.any { target.startsWith(it) }) {
                        violations += "${file.path}:${idx + 1}: $line"
                    }
                }
            }
        }
        assertTrue(
            "Namespaces family.crypto.* / family.pairing.* / family.keys.* all " +
                "removed (renamed to cryptokit.* in TASK-51 Phase 4 and TASK-56).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun scanRepo(): Sequence<File> {
        val root = locateRepoRoot()
        return root.walkTopDown()
            .onEnter { dir -> !isExcludedDir(dir) }
            .filter { it.isFile && it.extension == "kt" }
            .filter { !isFitnessFile(it) }
    }

    private fun isExcludedDir(dir: File): Boolean {
        val name = dir.name
        return name == "build" || name == ".gradle" || name == ".git" ||
            name == "node_modules" || name == "specs" || name == "docs" ||
            name == "backlog"
    }

    private fun isFitnessFile(file: File): Boolean =
        file.path.replace('\\', '/').contains("/test/fitness/")

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(5) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        return File(System.getProperty("user.dir"))
    }
}
