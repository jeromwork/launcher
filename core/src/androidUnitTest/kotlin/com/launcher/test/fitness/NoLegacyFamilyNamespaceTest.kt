package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-141 — fitness rule: zero `cryptokit.*` imports anywhere. The extractable crypto family
 * lives under one root, `family.*`:
 *  - `family.crypto.*`  (`:core:crypto`)
 *  - `family.pairing.*` (`:core:crypto`)
 *  - `family.keys.*`    (`:core:keys`)
 *  - `family.push.*`    (`:core:push`)
 *  - `family.wire.*`    (`:core:wire`)
 *
 * ## Why this rule reversed direction
 *
 * It used to ban `family.*` and require `cryptokit.*` (TASK-51 + TASK-56, on the 2026-06-26
 * mandate "the word family bothers me"). On 2026-07-20 the owner clarified the objection was to
 * reading `family` as the target AUDIENCE; in the sense of a family OF PRODUCTS (launcher,
 * messenger, gallery) the root is accepted, and `cryptokit` is rejected as an umbrella because
 * it names one module inside the extractable set rather than the set itself. TASK-56 is
 * superseded; this guard now points the other way so the old root cannot creep back.
 *
 * Whitelist: fitness tests + specs/ + docs/ + backlog/ (historical prose only).
 */
class NoLegacyFamilyNamespaceTest {

    @Test
    fun nothingImports_legacyCryptokitNamespace() {
        val violations = mutableListOf<String>()
        scanRepo().forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { idx, raw ->
                    val line = raw.trim()
                    if (!line.startsWith("import ")) return@forEachIndexed
                    val target = line.removePrefix("import ").removeSuffix(";").trim()
                    if (target.startsWith("cryptokit.")) {
                        violations += "${file.path}:${idx + 1}: $line"
                    }
                }
            }
        }
        assertTrue(
            "The `cryptokit.*` namespace is retired — the crypto family is `family.*` " +
                "(TASK-141, superseding TASK-56).\nViolations:\n${violations.joinToString("\n")}",
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
