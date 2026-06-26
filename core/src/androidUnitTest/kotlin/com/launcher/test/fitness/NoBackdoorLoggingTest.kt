package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-51 T073 — fitness rule: `Log.w("cryptokit", ...)` / `Log.e("cryptokit", ...)`
 * calls in production code MUST NOT contain raw secret-material patterns
 * (FR-017). Allowed fields: `operation`, `exceptionClass`, `messageHash`.
 *
 * Detection (file-walker, not AST-aware):
 *   1. Find every line in production sources that calls `Log.w("cryptokit"` or `Log.e("cryptokit"`.
 *   2. Reject the line if its arguments contain forbidden patterns:
 *        - hex literal ≥ 16 chars (`"[0-9a-fA-F]{16,}"`)
 *        - the words `deviceId`, `privKey`, `privBytes`, `secret`, `plaintext`,
 *          `cek`, `bytes` followed by `=` and a `$` interpolation
 *        - `.toHex()` / `.toString()` on a ByteArray
 *
 * This is a "best-effort" string-based rule sufficient for the small surface
 * we currently log against the `cryptokit` tag. If a more sophisticated rule
 * is needed later, swap to Konsist AST-walk.
 */
class NoBackdoorLoggingTest {

    @Test
    fun cryptokit_tagged_log_calls_do_not_leak_secret_material() {
        val violations = mutableListOf<String>()
        val cryptokitTagPattern = Regex(
            """Log\.[we]\(\s*"cryptokit"|Log\.[we]\(\s*LOG_TAG""",
        )
        val forbiddenInArgs = listOf(
            Regex("\"[0-9a-fA-F]{16,}\""),                // long hex literal
            Regex("""\bdeviceId\s*[:=]"""),
            Regex("""\bprivKey\b"""),
            Regex("""\bprivBytes\b"""),
            Regex("""\bplaintext\b"""),
            Regex("""\bcek\b"""),
            Regex("""\.toHex\(\)"""),
        )
        scanProduction().forEach { file ->
            file.useLines { lines ->
                val all = lines.toList()
                all.forEachIndexed { idx, raw ->
                    if (!cryptokitTagPattern.containsMatchIn(raw)) return@forEachIndexed
                    // Collect up to 3 lines after to cover multi-line call args.
                    val window = (idx until minOf(idx + 4, all.size))
                        .joinToString("\n") { all[it] }
                    forbiddenInArgs.forEach { pat ->
                        if (pat.containsMatchIn(window)) {
                            violations += "${file.path}:${idx + 1}: cryptokit log leaks secret " +
                                "(pattern ${pat.pattern}):\n$window"
                        }
                    }
                }
            }
        }
        assertTrue(
            "FR-017: Log.{w,e}(\"cryptokit\", ...) must only emit " +
                "[operation, exceptionClass, messageHash]. No raw bytes / hex / " +
                "deviceIds / keys.\nViolations:\n${violations.joinToString("\n\n")}",
            violations.isEmpty(),
        )
    }

    private fun scanProduction(): Sequence<File> {
        val root = locateRepoRoot()
        return root.walkTopDown()
            .onEnter { dir -> !isExcludedDir(dir) }
            .filter { it.isFile && it.extension == "kt" }
            .filter { !isTestSource(it) && !isFitnessFile(it) }
    }

    private fun isExcludedDir(dir: File): Boolean {
        val name = dir.name
        return name == "build" || name == ".gradle" || name == ".git" ||
            name == "node_modules" || name == "specs" || name == "docs" ||
            name == "backlog"
    }

    private fun isTestSource(file: File): Boolean {
        val p = file.path.replace('\\', '/')
        return p.contains("/src/test/") ||
            p.contains("/src/androidUnitTest/") ||
            p.contains("/src/androidInstrumentedTest/") ||
            p.contains("/src/commonTest/") ||
            p.contains("/src/jvmTest/") ||
            p.contains("/src/androidRealBackendUnitTest/")
    }

    private fun isFitnessFile(file: File): Boolean =
        file.path.replace('\\', '/').contains("/test/fitness/")

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(5) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        return File(System.getProperty("user.dir"))
    }
}
