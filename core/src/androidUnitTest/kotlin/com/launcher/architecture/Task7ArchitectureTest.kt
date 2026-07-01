package com.launcher.architecture

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * TASK-7 architecture fitness functions (CLAUDE.md rule 7 — fitness over
 * manual review for machine-judgeable invariants).
 *
 * Initial gate (T7-004 only at Phase 0): CheckSpec.kt and ApplySpec.kt do
 * NOT import Android types — they remain pure commonMain so future iOS /
 * Android-TV adapter modules add their own variants without touching the
 * sealed hierarchy (Article VII §15 multi-platform seam).
 *
 * Implemented as a string scan (matches the [com.launcher.test.fitness.DomainIsolationTest]
 * pattern) rather than Konsist because androidUnitTest's user.dir flips
 * between the project root and :core/ depending on the runner.
 *
 * Additional gates T7-001..T7-006 are added in Phase 7 (T059).
 */
class Task7ArchitectureTest {

    private val commonMainDir: File by lazy {
        // Resolve regardless of working dir (project root vs :core/)
        val candidates = listOf(
            File("core/src/commonMain/kotlin/com/launcher/api/wizard/data"),
            File("src/commonMain/kotlin/com/launcher/api/wizard/data"),
            File("../core/src/commonMain/kotlin/com/launcher/api/wizard/data"),
        )
        candidates.firstOrNull { it.exists() }
            ?: error("commonMain data dir not found from ${File(".").absolutePath}")
    }

    private val commonMainRoot: File by lazy {
        val candidates = listOf(
            File("core/src/commonMain/kotlin"),
            File("src/commonMain/kotlin"),
            File("../core/src/commonMain/kotlin"),
        )
        candidates.firstOrNull { it.exists() }
            ?: error("commonMain root not found from ${File(".").absolutePath}")
    }

    private val projectRoot: File by lazy {
        val candidates = listOf(
            File("."),
            File(".."),
        )
        candidates.firstOrNull { File(it, "core/src/commonMain").exists() || File(it, "settings.gradle.kts").exists() }
            ?: error("project root not found from ${File(".").absolutePath}")
    }

    @Test
    fun t7_001_noGradleModuleNamedAfterProfile() {
        // T7-001 — profiles ship as bundled JSON, NEVER as Gradle modules
        // (Article VII §13). If we ever spot a `core/simple-launcher/` or
        // `feature-simple-launcher/` directory, the architecture is being
        // bent the wrong way.
        val offenders = mutableListOf<String>()
        listOf(File(projectRoot, "core"), File(projectRoot, "app"), projectRoot)
            .filter { it.isDirectory }
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .filter { it.isDirectory && it.name.contains("simple-launcher", ignoreCase = true) }
            .forEach { offenders += it.absolutePath }
        assertFalse(
            "no module / directory name may contain 'simple-launcher'; found: $offenders",
            offenders.isNotEmpty(),
        )
    }

    @Test
    fun t7_002_noProfileBranchingInBusinessLogic() {
        // T7-002 — never gate behaviour on `appFamilyId == "simple-launcher"`.
        // The profile is selected by which manifest the device loaded; the
        // engine, the adapters, and the UI must stay profile-agnostic.
        // Scope: kotlin files under core/src/{commonMain,androidMain} and
        // app/src/main. Tests / fixtures / mocks are skipped.
        val offenders = mutableListOf<String>()
        val scanRoots = listOf(
            File(projectRoot, "core/src/commonMain/kotlin"),
            File(projectRoot, "core/src/androidMain/kotlin"),
            File(projectRoot, "app/src/main"),
        ).filter { it.isDirectory }
        val regex = Regex("""(appFamilyId\s*==\s*"simple-launcher"|when\s*\(\s*appFamilyId\s*\))""")
        scanRoots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }
                .forEach { file ->
                    val text = file.readText()
                    if (regex.containsMatchIn(text)) {
                        offenders += "${file.absolutePath}: " + regex.find(text)?.value
                    }
                }
        }
        assertFalse(
            "profile-id branching is forbidden in business logic; found:\n  " + offenders.joinToString("\n  "),
            offenders.isNotEmpty(),
        )
    }

    @Test
    fun t7_003_configKindHasExactlyFiveValues() {
        // T7-003 — Article VII §10. Adding a sixth wire format here is a
        // schema-version change; bumping ConfigKind silently is forbidden.
        val configKind = com.launcher.api.wizard.ConfigKind::class.java
        val expected = setOf(
            "WizardManifest",
            "ScreenLayout",
            "TileSet",
            "SystemSettingsPool",
            "UICustomizationPool",
            // TASK-65 (T614) — preset wire format added; ConfigKind grew to 6.
            "Preset",
        )
        val actual = configKind.enumConstants.map { (it as Enum<*>).name }.toSet()
        org.junit.Assert.assertEquals(
            "ConfigKind must declare exactly the 6 documented wire formats",
            expected,
            actual,
        )
    }

    @Test
    fun t7_006_bundledJsonsHaveSchemaVersion() {
        // T7-006 — every bundled JSON in core/src/androidMain/assets/wizard/
        // declares `schemaVersion >= 1` (CLAUDE.md rule 5). Missing field
        // means the file is unversioned and any future read against a
        // bumped reader corrupts state.
        val assetsRoot = File(projectRoot, "core/src/androidMain/assets/wizard")
        assertTrue("assets dir missing", assetsRoot.isDirectory)
        val missing = mutableListOf<String>()
        assetsRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".json") }
            .forEach { file ->
                val text = file.readText()
                val match = Regex(""""schemaVersion"\s*:\s*(\d+)""").find(text)
                val v = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (v == null || v < 1) {
                    missing += "${file.absolutePath} (schemaVersion=$v)"
                }
            }
        assertFalse(
            "every bundled JSON must declare schemaVersion >= 1; offenders: $missing",
            missing.isNotEmpty(),
        )
    }

    @Test
    fun t7_004_checkSpec_hasNoAndroidImports() {
        val file = File(commonMainDir, "CheckSpec.kt")
        assertTrue("CheckSpec.kt exists at ${file.absolutePath}", file.exists())
        val text = file.readText()
        val forbiddenImports = forbiddenAndroidImports(text)
        assertFalse(
            "CheckSpec.kt MUST NOT import Android / vendor types; found: $forbiddenImports",
            forbiddenImports.isNotEmpty(),
        )
    }

    @Test
    fun t7_005_appCompatDelegate_notReferencedFromCommonMain() {
        // T7-005 — AppCompatDelegate.setApplicationLocales is the Android
        // shim for per-app locale. It belongs in `app/` (Activity / Application)
        // or in `core/androidMain/` adapter modules, NEVER in commonMain
        // (FR-020, FR-030). Future iOS adapter has its own locale-override path.
        val offenders = mutableListOf<String>()
        commonMainRoot.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }
            .forEach { file ->
                val text = file.readText()
                if (text.contains("AppCompatDelegate") ||
                    text.contains("androidx.appcompat.app.AppCompatDelegate")
                ) {
                    offenders += file.absolutePath
                }
            }
        assertFalse(
            "AppCompatDelegate MUST NOT be referenced from commonMain; found in: $offenders",
            offenders.isNotEmpty(),
        )
    }

    @Test
    fun t7_004_applySpec_hasNoAndroidImports() {
        val file = File(commonMainDir, "ApplySpec.kt")
        assertTrue("ApplySpec.kt exists at ${file.absolutePath}", file.exists())
        val text = file.readText()
        val forbiddenImports = forbiddenAndroidImports(text)
        assertFalse(
            "ApplySpec.kt MUST NOT import Android / vendor types; found: $forbiddenImports",
            forbiddenImports.isNotEmpty(),
        )
    }

    private fun forbiddenAndroidImports(source: String): List<String> {
        val forbiddenPrefixes = listOf(
            "android.",
            "androidx.activity.",
            "androidx.appcompat.",
            "androidx.core.",
            "androidx.lifecycle.",
            "androidx.datastore.",
            "com.google.firebase.",
            "com.google.android.",
        )
        return source.lines()
            .map { it.trim() }
            .filter { it.startsWith("import ") }
            .mapNotNull { line ->
                val pkg = line.removePrefix("import ").substringBefore(' ')
                forbiddenPrefixes.firstOrNull { pkg.startsWith(it) }?.let { _ -> pkg }
            }
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
