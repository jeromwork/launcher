package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 008 isolation fitness tests (Phase 10 T120-T123).
 *
 * Konsist gates protecting boundaries introduced by spec 008:
 *
 *  - **T120**: `commonMain/api/config/` is vendor-clean — no Firebase, no
 *    Android. (Covered by [DomainIsolationTest.commonMain_doesNotImportAndroidApis]
 *    universal scan; this test adds spec 008-specific assertions.)
 *
 *  - **T121**: `commonMain/api/lifecycle/` clean of `android.net.*` and
 *    `androidx.lifecycle.*`. Adapters wrap these platform types.
 *
 *  - **T122**: SQLDelight `SqlDriver` type doesn't leak past
 *    `androidMain/adapters/config/AndroidSqlDriverProvider.kt`. Only that
 *    file imports `app.cash.sqldelight.db.SqlDriver` или its concrete drivers.
 *
 *  - **T123**: UI does NOT import LocalConfigStore directly — access through
 *    ConfigEditor / ConfigApplier ports only.
 *
 * Pattern follows [DomainIsolationTest] — plain file-system scan; resilient
 * to gradle cwd variations across androidUnitTest runners.
 */
class Spec008IsolationTest {

    @Test
    fun commonMain_config_package_does_not_import_vendor() {
        val configDir = locateCommonMain().resolve("kotlin/com/launcher/api/config")
        if (!configDir.isDirectory) return // package not yet created — pass.

        val forbidden = listOf(
            "com.google.firebase.",
            "android.",
            "androidx.lifecycle.",
            "androidx.work.",
        )
        val violations = scanImports(configDir, forbidden)
        assertTrue(
            "commonMain/api/config/ must NOT import vendor / platform types (spec 008 plan §Konsist).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun commonMain_lifecycle_package_clean_of_android_net_lifecycle() {
        val lifecycleDir = locateCommonMain().resolve("kotlin/com/launcher/api/lifecycle")
        if (!lifecycleDir.isDirectory) return

        val forbidden = listOf(
            "android.net.",
            "androidx.lifecycle.",
            "com.google.firebase.",
        )
        val violations = scanImports(lifecycleDir, forbidden)
        assertTrue(
            "commonMain/api/lifecycle/ must NOT import platform lifecycle types (spec 008 plan §Konsist).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun sqlDriver_does_not_leak_past_AndroidSqlDriverProvider() {
        // Spec 008 plan §Konsist: SqlDriver type instances confined to
        // AndroidSqlDriverProvider. Other files в androidMain/adapters/config/
        // talk in terms of `ConfigStore` (KMP-pure SQLDelight wrapper).
        val androidMainConfigDir = locateProjectRoot().resolve("core/src/androidMain/kotlin/com/launcher/adapters/config")
        if (!androidMainConfigDir.isDirectory) return

        val allowedFiles = setOf("AndroidSqlDriverProvider.kt")
        val violations = androidMainConfigDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name !in allowedFiles }
            .flatMap { file ->
                file.readLines().asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("import app.cash.sqldelight.db.SqlDriver") || it.startsWith("import app.cash.sqldelight.driver.android.") }
                    .map { "${file.name}: $it" }
            }
            .toList()

        assertTrue(
            "SqlDriver imports MUST be confined to AndroidSqlDriverProvider.kt.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun ui_layer_does_not_import_LocalConfigStore_directly() {
        // UI should access config through ConfigEditor / ConfigApplier ports
        // только. Direct LocalConfigStore use в UI would couple UI to
        // persistence layer (per data-model.md §Domain ports inventory).
        val uiDirs = listOf(
            locateCommonMain().resolve("kotlin/com/launcher/ui"),
            locateProjectRoot().resolve("app/src/main/kotlin"),
        ).filter { it.isDirectory }

        val violations = uiDirs.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().asSequence()
                        .map { it.trim() }
                        .filter { it == "import com.launcher.api.config.LocalConfigStore" }
                        .map { "${file.path}: $it" }
                }
                .toList()
        }

        assertTrue(
            "UI files must not import LocalConfigStore directly — go through ConfigEditor/ConfigApplier.\n" +
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

    private fun locateProjectRoot(): File {
        val cwd = File(System.getProperty("user.dir"))
        // androidUnitTest runs with cwd = :core, fitness tests с repo root.
        return when {
            File(cwd, "core").isDirectory -> cwd
            cwd.name == "core" -> cwd.parentFile
            else -> cwd
        }
    }
}
