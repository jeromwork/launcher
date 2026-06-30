package com.launcher.lint

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractionReadinessDetectorTest {

    private val rule = ExtractionReadinessDetector()

    @Test
    fun flagsAppLayerImportInsidePresetPackage() {
        val code = """
            package com.launcher.api.preset
            import com.launcher.app.tiles.Tile
            class Bad
        """.trimIndent()
        val findings = rule.lint(code, fileName = "com/launcher/api/preset/Bad.kt")
        assertTrue(findings.isNotEmpty(), "expected finding for forbidden import")
    }

    @Test
    fun allowsSameImportInsideAppPackage() {
        val code = """
            package com.launcher.app.foo
            import com.launcher.app.tiles.Tile
            class Ok
        """.trimIndent()
        val findings = rule.lint(code, fileName = "com/launcher/app/foo/Ok.kt")
        assertEquals(0, findings.size, "non-foundation package should be ignored: $findings")
    }
}
