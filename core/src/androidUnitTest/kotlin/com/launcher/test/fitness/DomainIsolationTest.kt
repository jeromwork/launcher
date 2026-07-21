package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 005 §8.1 / CLAUDE.md rule 1: domain code (`commonMain`) MUST NOT
 * import platform / vendor types. Detects regressions early — if a future
 * change moves Android-specific code into commonMain, this test fails on
 * `./gradlew :core:test`.
 *
 * Implemented as a plain string scan rather than via Konsist because the
 * test runs in androidUnitTest where user.dir flips between the project
 * root and the :core module dir depending on the runner; Konsist 0.17.3
 * always resolves paths against user.dir which makes that fragile.
 *
 * Forbidden import roots in commonMain:
 *  - `android.*` — raw Android SDK
 *  - `com.google.firebase.*` — Firebase SDK (spec 007 T102, FR-013, CHK001):
 *    every Firebase touch belongs in the `androidRealBackend` source-set
 *    adapter modules; the domain talks only through ports
 *    (`RemoteSyncBackend`, `IdentityProvider`, `PushSender`, `LinkRegistry`).
 *
 * `androidx.compose.*` is **allowed**: Compose Multiplatform is the chosen
 * UI stack per ADR-005, and it runs natively in commonMain. Other
 * `androidx.*` packages (lifecycle, datastore, etc.) are still off-limits;
 * if one is added here in the future, it is a real violation and the
 * adapter belongs in androidMain.
 */
class DomainIsolationTest {

    private val forbiddenPrefixes = listOf(
        "android.",
        "com.google.firebase.",
    )
    private val forbiddenAndroidxPrefixes = listOf(
        "androidx.activity.",
        "androidx.appcompat.",
        "androidx.core.",
        "androidx.datastore.",
        "androidx.fragment.",
        "androidx.lifecycle.",
        "androidx.preference.",
        "androidx.work.",
    )

    /**
     * Vendor serialization frameworks — reflection-based or annotation-heavy mappers pulling in
     * an external runtime. Forbidden in the domain (CLAUDE.md rule 1, decided in TASK-144).
     *
     * `kotlinx.serialization.*` is deliberately ABSENT — it is the one allowed serializer, because
     * it is a compile-time Kotlin language facility with no runtime reflection and no vendor lock
     * (TASK-144 Decision). Every other serializer is infrastructure and belongs in an adapter; add
     * new vendors here as they appear. Firebase document mapping is already covered by the
     * `com.google.firebase.` prefix above.
     */
    private val forbiddenSerializerPrefixes = listOf(
        "com.fasterxml.jackson.",
        "com.google.gson.",
        "com.squareup.moshi.",
        "org.simpleframework.xml.",
        "javax.persistence.",
        "jakarta.persistence.",
    )

    @Test
    fun commonMain_doesNotImportAndroidApis() {
        val root = locateCommonMain()
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").removeSuffix(";").trim() }
                    .filter { importName ->
                        forbiddenPrefixes.any { importName.startsWith(it) } ||
                            forbiddenAndroidxPrefixes.any { importName.startsWith(it) }
                    }
                    .map { "${file.path}: import $it" }
            }
            .toList()
        assertTrue(
            "commonMain must not import Android APIs (CLAUDE.md rule 1, spec 005 §8.1).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun commonMain_usesOnlyKotlinxSerialization() {
        val root = locateCommonMain()
        val kotlinFiles = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        // Guard against a vacuous pass — if the scan reaches nothing, the rule proves nothing.
        check(kotlinFiles.size > 50) {
            "Only ${kotlinFiles.size} commonMain files scanned — the domain scan is not reaching " +
                "the codebase, so this rule would pass vacuously."
        }
        val violations = kotlinFiles.flatMap { file ->
            file.readLines().asSequence()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .map { it.removePrefix("import ").removeSuffix(";").trim() }
                .filter { importName -> forbiddenSerializerPrefixes.any { importName.startsWith(it) } }
                .map { "${file.path}: import $it" }
        }.toList()
        assertTrue(
            "The domain serializes with kotlinx.serialization only — a compile-time, " +
                "reflection-free, vendor-free Kotlin facility (CLAUDE.md rule 1, TASK-144). " +
                "A vendor serializer (Jackson/Gson/Moshi/JPA) in the domain is infrastructure and " +
                "belongs in an adapter.\nViolations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    /** Tries multiple roots — works whether tests run with cwd = repo root or = :core. */
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
