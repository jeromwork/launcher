package com.launcher.lint

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresetIdBranchingDetectorTest {

    private val rule = PresetIdBranchingDetector()

    private fun lint(fileName: String, content: String) =
        rule.lint(compileContentForTest(content, fileName))

    @Test
    fun flagsIfPresetIdEqualsInAppHomePackage() {
        val findings = lint(
            "com/launcher/app/home/Bad.kt",
            """
                package com.launcher.app.home
                fun route(presetId: String) {
                    if (presetId == "x") println("a") else println("b")
                }
            """.trimIndent(),
        )
        assertTrue(findings.isNotEmpty(), "expected a finding, got none")
    }

    @Test
    fun flagsWhenPresetIdInCoreWizard() {
        val findings = lint(
            "com/launcher/api/wizard/Bad.kt",
            """
                package com.launcher.api.wizard
                fun route(presetId: String) = when (presetId) {
                    "a" -> 1
                    else -> 0
                }
            """.trimIndent(),
        )
        assertTrue(findings.isNotEmpty(), "expected a finding, got none")
    }

    @Test
    fun allowsPresetIdBranchInsidePresetPackage() {
        val findings = lint(
            "com/launcher/api/preset/Whitelisted.kt",
            """
                package com.launcher.api.preset
                fun debug(presetId: String) {
                    if (presetId == "test") println("ok")
                }
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "preset package is whitelisted: $findings")
    }

    @Test
    fun allowsArchitectureTests() {
        val findings = lint(
            "com/launcher/architecture/FitnessTest.kt",
            """
                package com.launcher.architecture
                val pattern = Regex("if \(presetId == \"x\"\)")
            """.trimIndent(),
        )
        assertEquals(0, findings.size)
    }
}
