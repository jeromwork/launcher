package com.launcher.lint

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractionReadinessDetectorTest {

    private val rule = ExtractionReadinessDetector()

    private fun lint(fileName: String, content: String) =
        rule.lint(compileContentForTest(content, fileName))

    @Test
    fun flagsAppLayerImportInsidePresetPackage() {
        val findings = lint(
            "com/launcher/api/preset/Bad.kt",
            """
                package com.launcher.api.preset
                import com.launcher.app.tiles.Tile
                class Bad
            """.trimIndent(),
        )
        assertTrue(findings.isNotEmpty(), "expected finding for forbidden import")
    }

    @Test
    fun allowsSameImportInsideAppPackage() {
        val findings = lint(
            "com/launcher/app/foo/Ok.kt",
            """
                package com.launcher.app.foo
                import com.launcher.app.tiles.Tile
                class Ok
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "non-foundation package should be ignored: $findings")
    }
}
