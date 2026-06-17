package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 015 isolation fitness tests (Phase 1 T006, T009, FR-038, FR-038a, FR-041).
 *
 * Architecture rules enforced:
 *
 *  - `com.launcher.api.wizard.*` MUST NOT depend on `com.launcher.ui.*`
 *    nor on `com.launcher.app.*` (domain stays UI-agnostic).
 *  - `com.launcher.api.localization.*` MUST NOT depend on `com.launcher.ui.*`
 *    nor on `com.launcher.app.*`.
 *  - `com.launcher.ui.senior.*` MUST NOT depend on `com.launcher.api.*`
 *    (primitives are self-contained — domain types passed in by host).
 *  - `com.launcher.ui.wizard.*` MAY depend on `api.wizard.*`, `api.localization.*`,
 *    and `ui.senior.*` only (no `app.*`).
 *
 * Pattern follows [DomainIsolationTest] — plain file-system scan; resilient to
 * gradle cwd variations across androidUnitTest runners. We do NOT use Konsist
 * here because the existing project pattern is string-scan.
 */
class Spec015IsolationTest {

    @Test
    fun api_wizard_does_not_depend_on_ui_or_app() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/wizard")
        if (!dir.isDirectory) return
        val forbidden = listOf(
            "com.launcher.ui.",
            "com.launcher.app.",
        )
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "api.wizard.* must not depend on ui.* or app.* (FR-038).\n" +
                "Reason: domain stays UI- and app-agnostic so the engine can be reused.\n" +
                "Suggested fix: move the offending reference into ui.wizard or :app.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun api_localization_does_not_depend_on_ui_or_app() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/localization")
        if (!dir.isDirectory) return
        val forbidden = listOf(
            "com.launcher.ui.",
            "com.launcher.app.",
        )
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "api.localization.* must not depend on ui.* or app.* (FR-038).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun ui_senior_does_not_depend_on_api_wizard() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/ui/senior")
        if (!dir.isDirectory) return
        // ui.senior is the reusable primitive layer — it must be free of
        // wizard-domain knowledge so it can be used in S-1+ home screens too.
        // ui.senior MAY depend on api.wizard.AnimationPreferenceProvider —
        // that's a generic cross-cutting port. Allow that one exception via
        // explicit prefix check rather than blanket api.* ban.
        val forbidden = listOf(
            "com.launcher.app.",
            "com.launcher.api.wizard.data.",     // wire-format types stay out of UI primitives
            "com.launcher.api.wizard.ConfigSource",
            "com.launcher.api.wizard.WizardEngine",
            "com.launcher.api.wizard.WizardCheckpoint",
            "com.launcher.api.wizard.SystemSettingPort",
            "com.launcher.api.wizard.UserPreferencesStore",
            "com.launcher.api.wizard.DismissedHintsStore",
        )
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "ui.senior.* must stay free of wizard-specific knowledge so the primitives can be reused in S-1+ home screens (FR-038).\n" +
                "Allowed exceptions: AnimationPreferenceProvider (generic cross-cutting port).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun ui_wizard_does_not_depend_on_app() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/ui/wizard")
        if (!dir.isDirectory) return
        val forbidden = listOf("com.launcher.app.")
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "ui.wizard.* must not depend on app.* — :app wires it in via DI.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun scanImports(root: File, forbiddenPrefixes: List<String>): List<String> =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").removeSuffix(";").trim() }
                    .filter { importName ->
                        forbiddenPrefixes.any { importName.startsWith(it) }
                    }
                    .map { "${file.path}: import $it" }
            }
            .toList()

    private fun locateCommonMain(): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/commonMain"),
            File(cwd, "core/src/commonMain"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("commonMain not found from cwd=$cwd among: ${candidates.map { it.path }}")
    }
}
