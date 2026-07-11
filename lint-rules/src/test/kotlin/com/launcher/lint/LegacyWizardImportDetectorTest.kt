package com.launcher.lint

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyWizardImportDetectorTest {

    private val rule = LegacyWizardImportDetector()

    private fun lint(fileName: String, content: String) =
        rule.lint(compileContentForTest(content, fileName))

    @Test
    fun flagsWizardEngineImportInApp() {
        val findings = lint(
            "com/launcher/app/home/Bad.kt",
            """
                package com.launcher.app.home
                import com.launcher.api.wizard.WizardEngine
                fun use(w: WizardEngine) = Unit
            """.trimIndent(),
        )
        assertTrue(findings.isNotEmpty(), "expected a finding, got none")
    }

    @Test
    fun flagsLegacyPresetImportInCore() {
        // CL-4 coverage: com.launcher.api.preset is also being retired.
        val findings = lint(
            "com/launcher/core/foo/Bad.kt",
            """
                package com.launcher.core.foo
                import com.launcher.api.preset.PresetSelectionService
                fun use(s: PresetSelectionService) = Unit
            """.trimIndent(),
        )
        assertTrue(findings.isNotEmpty(), "expected a finding, got none")
    }

    @Test
    fun allowsNewPresetNamespace() {
        // com.launcher.preset.* is the new namespace, must NOT be flagged.
        val findings = lint(
            "com/launcher/app/home/Good.kt",
            """
                package com.launcher.app.home
                import com.launcher.preset.ReconcileEngine
                fun use(r: ReconcileEngine) = Unit
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "new preset namespace must not be flagged: $findings")
    }

    @Test
    fun ignoresFilesInLegacyOwnerPackages() {
        // Files that DECLARE the legacy packages themselves must not self-report
        // during the Phase 6a → Phase 6b window (Phase 6b deletes them).
        val findings = lint(
            "com/launcher/api/wizard/Bar.kt",
            """
                package com.launcher.api.wizard
                import com.launcher.api.wizard.internal.Foo
                class Bar
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "owner package is whitelisted: $findings")
    }

    @Test
    fun flagsExactPackageImport() {
        val findings = lint(
            "com/launcher/app/Foo.kt",
            """
                package com.launcher.app
                import com.launcher.api.wizard.WizardStep
            """.trimIndent(),
        )
        assertTrue(findings.isNotEmpty(), "expected a finding, got none")
    }

    @Test
    fun doesNotFlagUnrelatedImports() {
        val findings = lint(
            "com/launcher/app/home/Ok.kt",
            """
                package com.launcher.app.home
                import kotlin.collections.List
                import com.launcher.core.model.Something
                fun x() = Unit
            """.trimIndent(),
        )
        assertEquals(0, findings.size)
    }
}
