package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 010 isolation fitness tests (Phase 0 T007..T010).
 *
 * Four Konsist gates protecting boundaries introduced by spec 010
 * (plan.md §11 C-1 / C-9, CHK-domain-001, CHK-domain-007, CHK-security-009).
 *
 *  - **T007**: `commonMain/api/setup/` MUST NOT import `android.*` /
 *    `androidx.*` / `com.google.android.gms.*` (domain isolation, rule 1
 *    in CLAUDE.md). The domain port for GMS availability is `commonMain`
 *    and may never see the vendor type.
 *
 *  - **T008**: `commonMain/api/gate/` MUST NOT import `android.*` /
 *    `androidx.*`. The 7-tap challenge gate domain types stay pure-Kotlin.
 *
 *  - **T009**: `IntentSpec` MUST contain only `String` / primitive fields —
 *    never raw `Intent` / `Uri` / `ComponentName` (CHK-domain-007). The
 *    domain says *what* to launch; the adapter constructs the Intent.
 *
 *  - **T010**: every Activity declared under `app/src/main/.../setup`,
 *    `app/src/main/.../gate`, `app/src/main/.../call`, `app/src/main/.../paired`
 *    MUST have `android:exported="false"` in `AndroidManifest.xml`
 *    (CHK-security-009, plan §11 C-9 — none of these are meant to be
 *    addressable from outside the app).
 */
class Spec010IsolationTest {

    // ─── T007: api/setup pure-Kotlin ─────────────────────────────────────

    @Test
    fun T007_api_setup_does_not_import_android_or_gms() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/setup")
        if (!dir.isDirectory) return  // package not yet created — defensive pass.
        val forbidden = listOf(
            "android.",
            "androidx.",
            "com.google.android.gms.",
        )
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "api/setup/ must NOT import android.* / androidx.* / com.google.android.gms.* " +
                "(spec 010 plan §11 C-1, CHK-domain-001).\nViolations:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    // ─── T008: api/gate pure-Kotlin ──────────────────────────────────────

    @Test
    fun T008_api_gate_does_not_import_android() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/gate")
        if (!dir.isDirectory) return
        val forbidden = listOf("android.", "androidx.")
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "api/gate/ must NOT import android.* / androidx.*.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T009: IntentSpec is a value bag of primitives ──────────────────

    @Test
    fun T009_intent_spec_contains_only_primitive_fields() {
        val file = locateCommonMain().resolve("kotlin/com/launcher/api/setup/IntentSpec.kt")
        if (!file.isFile) return  // not yet created.
        // Only inspect property/parameter declarations — kdoc commentary is allowed
        // to discuss platform types like Intent/Uri/Bundle as long as none of them
        // appear as the actual type of a class member.
        val declarationLines = file.readLines()
            .map { it.trim() }
            .filter { it.startsWith("val ") || it.startsWith("var ") }
        val forbiddenTypes = listOf("Intent", "Uri", "ComponentName", "Bundle")
        val violations = declarationLines.filter { line ->
            // Pull the declared type — everything after the first `:` up to `=` / `,` / end.
            val colon = line.indexOf(':')
            if (colon < 0) return@filter false
            val rawType = line.substring(colon + 1).substringBefore('=').substringBefore(',')
            forbiddenTypes.any { type ->
                Regex("\\b$type\\b").containsMatchIn(rawType)
            }
        }
        assertTrue(
            "IntentSpec must contain only String / primitive fields (CHK-domain-007).\n" +
                "Violating declarations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T010: spec-010 Activities are not exported ─────────────────────

    @Test
    fun T010_spec010_activities_are_not_exported() {
        val manifest = locateAppManifest() ?: return  // resilient.
        val text = manifest.readText()
        // The check is structural: every <activity ...> block whose android:name
        // contains one of the spec-010 sub-packages must declare exported="false".
        val spec010ActivityRegex = Regex(
            "<activity[\\s\\S]*?android:name=\"[^\"]*(setup|gate|call|paired|hardblock)[^\"]*\"[\\s\\S]*?/>",
            RegexOption.IGNORE_CASE,
        )
        val violations = spec010ActivityRegex.findAll(text)
            .filterNot { it.value.contains("android:exported=\"false\"") }
            .map { it.value.lines().first() }
            .toList()
        assertTrue(
            "Spec 010 Activities under setup/gate/call/paired/hardblock packages " +
                "must declare android:exported=\"false\" (plan §11 C-9, " +
                "CHK-security-009).\nViolating activity declarations:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    // ─── helpers (same pattern as Spec009IsolationTest) ──────────────────

    private fun scanImports(
        root: File,
        forbiddenPrefixes: List<String>,
    ): List<String> =
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

    private fun locateAppManifest(): File? {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "app/src/main/AndroidManifest.xml"),
            File(cwd, "../app/src/main/AndroidManifest.xml"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
