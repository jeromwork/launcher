package com.launcher.lint

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresetIdBranchingDetectorTest {

    private val rule = PresetIdBranchingDetector()

    @Test
    fun flagsIfPresetIdEqualsInAppHomePackage() {
        val code = """
            package com.launcher.app.home
            fun route(presetId: String) {
                if (presetId == "x") println("a") else println("b")
            }
        """.trimIndent()
        val findings = rule.lint(code, fileName = "com/launcher/app/home/Bad.kt")
        assertTrue(findings.isNotEmpty(), "expected a finding, got none")
    }

    @Test
    fun flagsWhenPresetIdInCoreWizard() {
        val code = """
            package com.launcher.api.wizard
            fun route(presetId: String) = when (presetId) {
                "a" -> 1
                else -> 0
            }
        """.trimIndent()
        val findings = rule.lint(code, fileName = "com/launcher/api/wizard/Bad.kt")
        assertTrue(findings.isNotEmpty(), "expected a finding, got none")
    }

    @Test
    fun allowsPresetIdBranchInsidePresetPackage() {
        val code = """
            package com.launcher.api.preset
            fun debug(presetId: String) {
                if (presetId == "test") println("ok")
            }
        """.trimIndent()
        val findings = rule.lint(code, fileName = "com/launcher/api/preset/Whitelisted.kt")
        assertEquals(0, findings.size, "preset package is whitelisted: $findings")
    }

    @Test
    fun allowsArchitectureTests() {
        val code = """
            package com.launcher.architecture
            val pattern = Regex("if \(presetId == \"x\"\)")
        """.trimIndent()
        val findings = rule.lint(code, fileName = "com/launcher/architecture/FitnessTest.kt")
        assertEquals(0, findings.size)
    }
}
