package com.launcher.cloud.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-49 T038 — fitness gate (Detekt-equivalent file-walker), mirror of
 * Spec017AuthIsolationTest. Enforces CLAUDE.md rule 1 (domain isolated
 * from infrastructure) on `:core:cloud/commonMain`.
 *
 * Detekt plugin не подключён к module — используем тот же паттерн scan-
 * imports, который применяется в Spec017 для AuthProvider.
 */
class Task49CloudIsolationTest {

    @Test
    fun commonMain_cloud_does_not_import_vendor_sdks() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/cloud")
        if (!dir.isDirectory) error("expected commonMain/cloud at $dir")
        val forbidden = listOf(
            "com.google.firebase",
            "com.google.android.gms",
            "com.google.android.libraries",
            "androidx.compose",
            "android.app",
            "android.content",
            "android.os",
            "android.telephony",
            "android.net",
        )
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "core/cloud/commonMain must NOT import vendor SDKs / Android platform types " +
                "(CLAUDE.md rule 1, TASK-49 plan §Test Strategy).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun commonMain_cloud_does_not_mention_provider_names_in_identifiers() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/cloud")
        if (!dir.isDirectory) error("expected commonMain/cloud at $dir")
        val forbiddenWords = listOf("Firebase", "Google", "Gms")
        val violations = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        return@forEachIndexed
                    }
                    forbiddenWords.forEach { word ->
                        if (trimmed.contains(word)) {
                            violations.add("${file.name}:${idx + 1}: identifier contains '$word' — $trimmed")
                        }
                    }
                }
            }
        assertTrue(
            "core/cloud/commonMain identifiers must NOT name providers " +
                "(CLAUDE.md rule 1, TASK-49 FR-001..FR-012).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun scanImports(root: File, forbiddenPrefixes: List<String>): List<String> =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .mapIndexed { idx, raw -> idx + 1 to raw.trim() }
                    .filter { it.second.startsWith("import ") }
                    .map { (lineNo, raw) ->
                        lineNo to raw.removePrefix("import ").removeSuffix(";").trim()
                    }
                    .filter { (_, name) -> forbiddenPrefixes.any { name.startsWith(it) } }
                    .map { (lineNo, name) -> "${file.path}:$lineNo: import $name" }
            }
            .toList()

    private fun locateCommonMain(): File {
        val cwd = File(System.getProperty("user.dir"))
        return listOf(
            File(cwd, "src/commonMain"),
            File(cwd, "core/cloud/src/commonMain"),
        ).firstOrNull { it.isDirectory }
            ?: error("commonMain not found from cwd=$cwd")
    }
}
