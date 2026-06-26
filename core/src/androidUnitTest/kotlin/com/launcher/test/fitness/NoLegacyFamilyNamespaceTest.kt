package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-51 T072 — fitness rule: zero `family.crypto.*` / `family.pairing.*`
 * imports anywhere. Phase 4 renamed the namespaces to `cryptokit.*`
 * (FR-007, FR-016, SC-012).
 *
 * NOTE: `family.keys.*` is intentionally EXCLUDED — that namespace is
 * the territory of follow-up TASK-56 (rename `family.keys.*` →
 * `cryptokit.keys.*`) and still lives in `core/keys/` at the time of
 * TASK-51 close.
 *
 * Whitelist: fitness tests + specs/ + docs/ + backlog/ + core/keys/
 * (still owns `family.keys.*` until TASK-56 lands).
 */
class NoLegacyFamilyNamespaceTest {

    @Test
    fun nothingImports_familyCrypto_or_familyPairing() {
        val violations = mutableListOf<String>()
        val forbiddenPrefixes = listOf("family.crypto", "family.pairing")
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
            "Namespaces family.crypto.* / family.pairing.* removed in TASK-51 " +
                "Phase 4 (renamed to cryptokit.*). family.keys.* remains until " +
                "TASK-56.\nViolations:\n${violations.joinToString("\n")}",
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
