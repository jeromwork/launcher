package com.launcher.test.fitness.preset

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-120 fitness functions #1 + #2 — the reconcile engine must NOT couple to
 * concrete Component subtypes (imports OR `when` matches). Rationale: engine
 * dispatches through ProviderRegistry; hard-coding subtype knowledge in the
 * engine defeats the port/adapter split (plan.md §6.6).
 */
class PresetEngineIsolationTest {

    private val engineDir by lazy { locateEngineDir() }

    private val forbiddenSubtypes = listOf(
        "com.launcher.preset.model.Component.AppTile",
        "com.launcher.preset.model.Component.FontSize",
        "com.launcher.preset.model.Component.Sos",
        "com.launcher.preset.model.Component.Toolbar",
    )

    @Test
    fun engine_doesNotImportConcreteComponentSubtypes() {
        val violations = engineDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").removeSuffix(";").trim() }
                    .filter { imp -> forbiddenSubtypes.any { imp == it } }
                    .map { "${file.path}: import $it" }
            }
            .toList()
        assertTrue(
            "Fitness #1: engine must NOT import concrete Component subtypes.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun engine_doesNotSwitchOnConcreteSubtypes() {
        val pattern = Regex("""when\s*\([^)]*component[^)]*\)\s*\{[^}]*is\s+Component\.(AppTile|FontSize|Sos|Toolbar)""")
        val violations = engineDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                if (pattern.containsMatchIn(text)) sequenceOf("${file.path}: when-on-subtype")
                else emptySequence()
            }
            .toList()
        assertTrue(
            "Fitness #2: engine must not `when(component) { is Component.<Subtype> ... }`.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun locateEngineDir(): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/commonMain/kotlin/com/launcher/preset/engine"),
            File(cwd, "core/src/commonMain/kotlin/com/launcher/preset/engine"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("preset/engine not found from cwd=$cwd")
    }
}
