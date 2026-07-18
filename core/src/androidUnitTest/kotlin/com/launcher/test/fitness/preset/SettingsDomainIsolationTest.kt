package com.launcher.test.fitness.preset

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-69 T069-029 (rule 1, plan.md §10) — `SettingsPresentationBuilder` /
 * `EngineSettingsGateway` (`preset/settings/`) and the `SettingsGateway` port
 * must not import Android APIs. Already covered broadly by
 * [com.launcher.test.fitness.DomainIsolationTest] (scans all of commonMain);
 * this test pins the specific TASK-69 files the way
 * [PresetEngineIsolationTest] pins the engine package.
 */
class SettingsDomainIsolationTest {

    private val forbiddenPrefixes = listOf("android.", "com.google.firebase.")

    @Test
    fun settingsDomainFiles_doNotImportAndroidApis() {
        val targets = listOf(
            locate("preset/settings"),
            locate("preset/port/SettingsGateway.kt"),
        )
        val violations = targets.flatMap { target ->
            val files = if (target.isDirectory) {
                target.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            } else {
                listOf(target)
            }
            files.flatMap { file ->
                file.readLines().asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").removeSuffix(";").trim() }
                    .filter { imp -> forbiddenPrefixes.any { imp.startsWith(it) } }
                    .map { "${file.path}: import $it" }
                    .toList()
            }
        }
        assertTrue(
            "TASK-69 T069-029: SettingsPresentationBuilder/EngineSettingsGateway/SettingsGateway must not import Android APIs.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun locate(relativePath: String): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/commonMain/kotlin/com/launcher/$relativePath"),
            File(cwd, "core/src/commonMain/kotlin/com/launcher/$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("$relativePath not found from cwd=$cwd among: ${candidates.map { it.path }}")
    }
}
