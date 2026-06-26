package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-51 T071 — fitness rule: zero references to the legacy crypto stack
 * removed in Phase 7 (FR-007, SC-007, SC-008):
 *  • `com.launcher.api.crypto.*` — old domain port surface.
 *  • `com.launcher.adapters.crypto.Libsodium*` — old lazysodium-backed adapters.
 *  • `com.launcher.adapters.crypto.AndroidKeystoreSecureKeystore` — old secure store.
 *
 * Scans anywhere in the repo (not just production) — these symbols literally
 * no longer exist; even test imports would break compilation.
 *
 * Whitelist: fitness tests + specs/ + docs/ + backlog/ (historical).
 */
class NoLegacyComLauncherCryptoTest {

    @Test
    fun nothingImports_legacyComLauncherCrypto() {
        val violations = mutableListOf<String>()
        val forbidden = listOf(
            "com.launcher.api.crypto",
            "com.launcher.adapters.crypto.Libsodium",
            "com.launcher.adapters.crypto.AndroidKeystoreSecureKeystore",
        )
        scanRepo().forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { idx, raw ->
                    val line = raw.trim()
                    if (!line.startsWith("import ")) return@forEachIndexed
                    val target = line.removePrefix("import ").removeSuffix(";").trim()
                    if (forbidden.any { target.startsWith(it) }) {
                        violations += "${file.path}:${idx + 1}: $line"
                    }
                }
            }
        }
        assertTrue(
            "Legacy com.launcher.{api,adapters}.crypto stack removed in TASK-51 Phase 7. " +
                "Re-introduction blocked (FR-007, SC-007, SC-008).\n" +
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
