package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 011 fitness gates (Phase 9, T110-T113).
 *
 *  - **T110**: `commonMain/api/crypto/` MUST NOT import vendor SDKs
 *    (`com.goterl.lazysodium.*`, `android.*`, `com.google.firebase.*`).
 *    CLAUDE.md rule 1 — domain isolation.
 *
 *  - **T111**: Lazysodium types confined в `adapters/crypto/Libsodium*.kt`;
 *    Firestore types — в `adapters/crypto/Firestore*.kt`;
 *    Firebase Storage — в `adapters/crypto/Firebase*.kt`.
 *
 *  - **T112**: No `Log.d/v/i/w/e` calls referencing plaintext/priv/cek/secret
 *    inside any crypto-related package. CLAUDE.md FR-051, FR-080.
 *
 *  - **T113**: No `throw` statements в `commonMain/api/crypto/` (кроме
 *    `init {}` validation blocks). All errors → Outcome<_, CryptoError>.
 */
class Spec011IsolationTest {

    // ─── T110: commonMain crypto pure-Kotlin ─────────────────────────────

    @Test
    fun T110_commonMain_crypto_does_not_import_vendor_sdks() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/crypto")
        if (!dir.isDirectory) return
        // TASK-51 T074 — extended ban list. The directory itself is gone after
        // Phase 7 (`com.launcher.api.crypto` collapsed into `family.*`),
        // so this test no-ops in practice — kept as structural guard against
        // accidental resurrection of the old layout.
        val forbidden = listOf(
            "com.goterl",                       // legacy lazysodium (TASK-51)
            "com.launcher.api.crypto",          // legacy port surface (TASK-51)
            "family.crypto",                    // pre-rename family.* (TASK-51 Phase 4)
            "android.",
            "androidx.",
            "com.google.firebase",
            "com.google.android",
        )
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "commonMain/api/crypto/ must NOT import vendor SDKs " +
                "(CLAUDE.md rule 1, spec 011 plan §Konsist gates, TASK-51 FR-007/SC-007).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T111: vendor types confined в adapters ──────────────────────────

    @Test
    fun T111_lazysodium_confined_to_libsodium_adapter_files() {
        val coreSrc = locateCoreSrc()
        val violations = mutableListOf<String>()
        coreSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                if (text.contains("com.goterl.lazysodium") &&
                    !file.path.contains("Libsodium") &&
                    !file.path.contains("AndroidKeystoreSecureKeystore") &&
                    !file.path.contains("fitness")  // fitness tests references whitelisted SDK names in strings
                ) {
                    violations.add("${file.path}: leaks com.goterl.lazysodium")
                }
            }
        assertTrue(
            "Lazysodium types must stay confined к Libsodium*.kt + " +
                "AndroidKeystoreSecureKeystore.kt (CLAUDE.md rule 1).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun T111_firestore_confined_to_realbackend_crypto_adapter_files() {
        val coreSrc = locateCoreSrc()
        val violations = mutableListOf<String>()
        // FirebaseFirestore = ОК в /sync/, /history/, /link/ (существующие
        // адаптеры из спека 007/008/009), но в /crypto/ — только FirestoreDeviceIdentityRepository.
        coreSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.path.contains("/crypto/") }
            .forEach { file ->
                val text = file.readText()
                if (text.contains("com.google.firebase.firestore") &&
                    !file.name.startsWith("Firestore")
                ) {
                    violations.add("${file.path}: leaks com.google.firebase.firestore")
                }
            }
        assertTrue(
            "Firestore types в crypto/ confined только к Firestore*.kt.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T112: no plaintext/key bytes в logs ─────────────────────────────

    @Test
    fun T112_no_secret_material_in_log_calls() {
        val coreSrc = locateCoreSrc()
        val violations = mutableListOf<String>()
        // Match Log.d/v/i/w/e (Android) или any function call with plaintext/priv/cek/secret keywords
        // в args. Conservative regex — avoid commenting code (//).
        val forbidden = Regex(
            "(Log\\.(d|v|i|w|e)|println)\\s*\\([^)]*(plaintext|priv\\b|cek\\b|secret|privKey|privBytes)[^)]*\\)",
            RegexOption.IGNORE_CASE,
        )
        coreSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.path.contains("/crypto/") }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                    if (forbidden.containsMatchIn(line)) {
                        violations.add("${file.path}:${idx + 1}: $trimmed")
                    }
                }
            }
        assertTrue(
            "No Log.* / println calls referencing plaintext/priv/cek/secret " +
                "in crypto packages (FR-051, FR-080).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T113: no Exception throw из commonMain/api/crypto ───────────────

    @Test
    fun T113_no_throw_outside_init_in_commonMain_crypto() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/crypto")
        if (!dir.isDirectory) return
        val violations = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                val lines = text.lines()
                var insideInit = false
                var braceDepthSinceInit = 0
                lines.forEachIndexed { idx, raw ->
                    val line = raw.trim()
                    // Track init {} blocks (allow throws там — require(...)).
                    if (line.startsWith("init") && line.contains("{")) {
                        insideInit = true
                        braceDepthSinceInit = 1
                        return@forEachIndexed
                    }
                    if (insideInit) {
                        braceDepthSinceInit += line.count { it == '{' }
                        braceDepthSinceInit -= line.count { it == '}' }
                        if (braceDepthSinceInit <= 0) insideInit = false
                        return@forEachIndexed
                    }
                    // Skip kdoc / line comments.
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                    // Detect explicit `throw ` statement (not in string literal — heuristic).
                    if (Regex("\\bthrow\\s+\\w").containsMatchIn(line)) {
                        // Allow `// throw` inside comments — already filtered.
                        // Allow `error(...)` (kotlin stdlib) — это другая семантика, частая в `check { }` блоках.
                        violations.add("${file.path}:${idx + 1}: $line")
                    }
                }
            }
        assertTrue(
            "No explicit throw outside init {} в commonMain/api/crypto " +
                "(CryptoError.* через Outcome).\nViolations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── helpers ─────────────────────────────────────────────────────────

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

    private fun locateCommonMain(): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/commonMain"),
            File(cwd, "core/src/commonMain"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("commonMain not found from cwd=$cwd")
    }

    private fun locateCoreSrc(): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src"),
            File(cwd, "core/src"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("core/src not found from cwd=$cwd")
    }
}
