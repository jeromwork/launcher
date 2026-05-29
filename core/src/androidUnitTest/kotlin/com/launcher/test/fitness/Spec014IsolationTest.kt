package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 014 fitness gates (Phase 0 scaffolding + Phase 8 enforcement).
 *
 *  - **T005** (Phase 0): skeleton — passes trivially. Test infrastructure ready.
 *  - **T170** (Phase 8): `commonMain/api/edit/` MUST NOT import vendor SDKs
 *    (`android.*`, `androidx.*`, `com.google.firebase.*`, `okhttp3.*`,
 *    `retrofit2.*`). CLAUDE.md rule 1 — domain isolation.
 *  - **T171** (Phase 8): no `expect`/`actual` declarations в
 *    `commonMain/api/edit/` — forces pure Kotlin (no platform-asymmetry).
 *
 * Pattern скопирован из [Spec011IsolationTest]; tests not yet implemented
 * for T170/T171 — добавятся в Phase 8 после того как Phase 1 domain types
 * landed (T010-T038).
 */
class Spec014IsolationTest {

    // ─── T005 (Phase 0): skeleton — verifies test infrastructure ─────────

    @Test
    fun T005_skeleton_passes_trivially() {
        // Phase 0 placeholder. Real rules — T170/T171 below.
        // Goal: confirm androidUnitTest source set builds + JUnit runs.
        val expected = 2 + 2
        assertTrue("trivial math", expected == 4)
    }

    // ─── T170 (Phase 8): commonMain/api/edit/ pure-Kotlin ────────────────
    // TODO(T170): uncomment when Phase 1 domain types landed.
    //
    // @Test
    // fun T170_commonMain_edit_does_not_import_vendor_sdks() {
    //     val dir = locateCommonMain().resolve("kotlin/com/launcher/api/edit")
    //     if (!dir.isDirectory) return
    //     val forbidden = listOf(
    //         "android.",
    //         "androidx.",
    //         "com.google.firebase",
    //         "com.google.android",
    //         "okhttp3.",
    //         "retrofit2.",
    //     )
    //     val violations = scanImports(dir, forbidden)
    //     assertTrue(
    //         "commonMain/api/edit/ must NOT import vendor SDKs " +
    //             "(CLAUDE.md rule 1, spec 014 plan §7.4).\n" +
    //             "Violations:\n${violations.joinToString("\n")}",
    //         violations.isEmpty(),
    //     )
    // }

    // ─── T171 (Phase 8): no expect/actual в commonMain/api/edit/ ─────────
    // TODO(T171): uncomment when Phase 1 domain types landed.
    //
    // @Test
    // fun T171_no_expect_actual_in_commonMain_edit() {
    //     val dir = locateCommonMain().resolve("kotlin/com/launcher/api/edit")
    //     if (!dir.isDirectory) return
    //     val violations = mutableListOf<String>()
    //     dir.walkTopDown()
    //         .filter { it.isFile && it.extension == "kt" }
    //         .forEach { file ->
    //             file.readLines().forEachIndexed { idx, line ->
    //                 val trimmed = line.trim()
    //                 if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
    //                 if (Regex("\\b(expect|actual)\\s+(class|fun|val|var|object|interface)\\b")
    //                         .containsMatchIn(line)) {
    //                     violations.add("${file.path}:${idx + 1}: $trimmed")
    //                 }
    //             }
    //         }
    //     assertTrue(
    //         "No expect/actual в commonMain/api/edit/ (pure Kotlin, " +
    //             "no platform asymmetry).\nViolations:\n${violations.joinToString("\n")}",
    //         violations.isEmpty(),
    //     )
    // }

    // ─── helpers (used by T170/T171 when uncommented) ────────────────────

    @Suppress("unused")
    private fun scanImports(root: File, forbiddenPrefixes: List<String>): List<String> =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").removeSuffix(";").trim() }
                    .filter { name -> forbiddenPrefixes.any { name.startsWith(it) } }
                    .map { "${file.path}: import $it" }
            }
            .toList()

    @Suppress("unused")
    private fun locateCommonMain(): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/commonMain"),
            File(cwd, "core/src/commonMain"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("commonMain not found from cwd=$cwd")
    }
}
