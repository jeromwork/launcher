package com.launcher.app.fitness

import org.junit.Test
import java.io.File

/**
 * TASK-140 — the architecture fitness rules, as ordinary unit tests.
 *
 * ## Why not Detekt
 *
 * These rules previously lived as custom Detekt detectors in `:lint-rules`. They
 * never ran. Detekt discovers custom rules through `ServiceLoader`, and its
 * plugin loader never registered ours — verified with `detekt { debug = true }`,
 * which lists only the nine built-in providers. Everything on our side checked
 * out: the jar carries a byte-correct
 * `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`, the jar
 * is on the task's `pluginClasspath`, the provider loads under a plain
 * `ServiceLoader`, and bytecode/JVM versions match. Root cause sits inside
 * Detekt 1.23.7's plugin loading on this Gradle/Kotlin combination.
 *
 * The decisive argument is not "Detekt is broken" but **silence**: a Detekt rule
 * that fails to load reports nothing and passes. From TASK-65 to 2026-07-20 the
 * board showed a green `detektFoundation` while zero rules ran. A JUnit test
 * cannot fail that way — if the file exists, Gradle runs it, and a rule that
 * stops matching surfaces as a failing test instead of as silence.
 *
 * ## Note on technique
 *
 * These scan source text rather than a parsed AST. Not a downgrade: the
 * detectors they replace also matched on element *text*
 * (`condText.contains("presetId ==")`). Konsist covers import- and
 * declaration-level rules (see [NoFakeCryptoInAppTest]); expression-level checks
 * like "no `when` over this identifier" fall outside its declaration-oriented
 * API, so those stay text-based.
 */
class ArchitectureFitnessTest {

    // --- WireFormatHygiene (TASK-16) ----------------------------------------

    @Test
    fun wireFormatVersionsComeFromNamedConstants() {
        val violations = productionSources().flatMap { file ->
            file.matches(VERSION_LITERAL) { "${it.trim()}" }
        }
        report(
            violations,
            "WireFormatHygiene",
            "A wire-format version must come from one named constant beside its type " +
                "(docs/architecture/wire-format.md §11), never a literal — otherwise the " +
                "value is a magic number with no single place to bump, and it drifts " +
                "between the model, its fixtures and its tests.",
        )
    }

    // --- PresetIdBranching (TASK-65 FR-020) ---------------------------------

    @Test
    fun noBranchingOnPresetIdentity() {
        val whitelist = listOf("com/launcher/api/preset/", "com/launcher/architecture/")
        val violations = productionSources()
            .filterNot { f -> whitelist.any { f.unixPath().contains(it) } }
            .flatMap { file -> file.matches(PRESET_ID_BRANCH) { it.trim() } }
        report(
            violations,
            "PresetIdBranching",
            "Do not branch on presetId outside the preset identity layer. Behaviour is " +
                "selected by configuration, not by asking which preset is running — a " +
                "`when(presetId)` has to be edited for every preset ever added.",
        )
    }

    // --- FF011LegacyWizardImport (TASK-126 FR-015 / NFR-003) ----------------

    @Test
    fun noLegacyWizardOrPresetApiImports() {
        val banned = listOf("com.launcher.api.wizard", "com.launcher.api.preset")
        // Files declaring the legacy packages themselves must not self-report.
        val owners = listOf("com/launcher/api/wizard/", "com/launcher/api/preset/")
        val violations = productionSources()
            .filterNot { f -> owners.any { f.unixPath().contains(it) } }
            .flatMap { file ->
                file.importMatches { fq -> banned.any { fq == it || fq.startsWith("$it.") } }
            }
        report(
            violations,
            "FF011LegacyWizardImport",
            "The legacy wizard/preset API packages are retired (TASK-126). Use " +
                "`com.launcher.preset.*` — a distinct namespace, not the `.api.` one.",
        )
    }

    // --- ExtractionReadiness (TASK-65 FR-021) -------------------------------

    @Test
    fun foundationPackagesDoNotImportAppLayer() {
        val foundation = listOf(
            "com/launcher/api/preset/", "com/launcher/api/profile/",
            "com/launcher/api/pools/", "com/launcher/api/switchstrategy/",
            "com/launcher/api/wizard/",
        )
        val forbidden = listOf(
            "com.launcher.app.tiles", "com.launcher.app.home", "com.launcher.app.contacts",
        )
        val violations = productionSources()
            .filter { f -> foundation.any { f.unixPath().contains(it) } }
            .flatMap { file ->
                file.importMatches { fq -> forbidden.any { fq.startsWith(it) } }
            }
        report(
            violations,
            "ExtractionReadiness",
            "Foundation packages must stay extractable into a standalone module, so " +
                "they must not depend on the app layer. Move the shared abstraction " +
                "down, or invert the dependency.",
        )
    }

    // --- helpers ------------------------------------------------------------

    private val repoRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found — no settings.gradle.kts up the tree")
    }

    /** Production Kotlin sources of the modules `detektFoundation` used to cover. */
    private fun productionSources(): List<File> =
        listOf("core", "app")
            .map { File(repoRoot, "$it/src") }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { f -> TEST_PATH_MARKERS.any { f.unixPath().contains(it) } }
            .also {
                check(it.size > 100) {
                    "Only ${it.size} production sources found — the scan is not " +
                        "reaching the codebase, so these rules would pass vacuously. " +
                        "That is the exact failure mode this test exists to prevent."
                }
            }

    private fun File.unixPath(): String = path.replace('\\', '/')

    private fun File.relativePath(): String = relativeTo(repoRoot).path.replace('\\', '/')

    private fun File.matches(regex: Regex, describe: (String) -> String): List<String> =
        readLines().withIndex().mapNotNull { (i, line) ->
            regex.find(line)?.let { "${relativePath()}:${i + 1} — ${describe(it.value)}" }
        }

    private fun File.importMatches(predicate: (String) -> Boolean): List<String> =
        readLines().withIndex().mapNotNull { (i, line) ->
            if (!line.startsWith("import ")) return@mapNotNull null
            val fq = line.removePrefix("import ").substringBefore(" as ").trim()
            if (predicate(fq)) "${relativePath()}:${i + 1} — $fq" else null
        }

    private fun report(violations: List<String>, rule: String, fix: String) {
        check(violations.isEmpty()) {
            "$rule — ${violations.size} violation(s):\n" +
                violations.joinToString("\n") { "  - $it" } + "\n\n$fix"
        }
    }

    private companion object {
        val TEST_PATH_MARKERS = listOf(
            "/test/", "/androidTest/", "/commonTest/", "/jvmTest/",
            "/androidUnitTest/", "/androidInstrumentedTest/", "/iosTest/",
            "/testFixtures/", "/build/",
        )

        /**
         * A version property initialised from a literal. A reference to a constant
         * (`= SCHEMA_VERSION`) does not match, and neither does a declaration with
         * no default — there the caller supplies the value.
         */
        val VERSION_LITERAL = Regex(
            """\b(?:val|var)\s+\w*(?:[sS]chemaVersion|[mM]inReaderVersion|[mM]inWriterVersion)\s*""" +
                """:\s*\w+\s*=\s*(?:\d|")""",
        )

        /**
         * Branching on preset identity: `when (presetId)` or an `if` whose condition
         * compares it. Deliberately NOT any `presetId ==` — the Detekt detector this
         * replaces only visited `if`/`when`, and a precondition like
         * `require(a.presetId == b.presetId)` asserts two presets match rather than
         * selecting behaviour per preset. Widening this flags that as a violation.
         */
        val PRESET_ID_BRANCH = Regex("""when\s*\(\s*presetId\s*\)|if\s*\([^)]*\bpresetId\s*==""")
    }
}
