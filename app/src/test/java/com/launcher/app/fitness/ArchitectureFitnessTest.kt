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

    // --- Wire-format strict mode (TASK-142) ---------------------------------
    //
    // Four rules, each calibrated against the codebase before being written: every one of them
    // is at zero violations today except where a documented exception says otherwise, so a
    // failure means something new broke rather than something old was never fixed.

    @Test
    fun serializableFormatsDeclareTheVersionHeader() {
        val violations = productionSources().flatMap { file ->
            file.serializableDeclarations()
                .filter { (_, body) -> VERSION_PROPERTY.containsMatchIn(body) }
                .filterNot { (_, body) -> body.contains(HEADER_INTERFACE) }
                .map { (name, _) -> "${file.relativePath()} — $name" }
        }
        report(
            violations,
            "WireVersionHeaderRequired",
            "A @Serializable type carrying a version must implement `WireVersionHeader` " +
                "(wire-format.md invariant I1). Implementing it is what forces all three " +
                "fields to exist: declare `schemaVersion` alone and a reader has no way to " +
                "tell 'I cannot read this' from 'I can read but must not write it back'.",
        )
    }

    @Test
    fun versionFieldsAreAlwaysEncoded() {
        val violations = productionSources()
            .filter { it.readText().contains(HEADER_INTERFACE) }
            .flatMap { file ->
                val text = file.readText()
                VERSION_FIELDS.mapNotNull { field ->
                    val at = Regex("""override\s+val\s+$field\s*:""").find(text)
                        ?: return@mapNotNull null
                    // Only the segment belonging to THIS parameter. Two traps here, both found by
                    // deliberately breaking the rule: a fixed-size lookback reaches into the
                    // neighbouring parameter and is satisfied by ITS annotation, and any lookback
                    // ending at the nearest '(' stops inside `@EncodeDefault(...)` itself. A
                    // parameter separator is a comma or open paren that ends its line.
                    val separator = PARAMETER_SEPARATOR
                        .findAll(text.substring(0, at.range.first))
                        .lastOrNull()?.range?.last ?: 0
                    val own = text.substring(separator, at.range.first)
                    if (own.contains("EncodeDefault")) null
                    else "${file.relativePath()} — $field"
                }
            }
        report(
            violations,
            "VersionFieldsAlwaysEncoded",
            "Each of the three version fields needs @EncodeDefault(EncodeDefault.Mode.ALWAYS). " +
                "Without it a field left at its default is omitted from the output entirely, so " +
                "the document ships with no version at all and the next reader cannot gate on " +
                "what is not there — invariant I1, and the exact bug hit during TASK-138.",
        )
    }

    @Test
    fun handWrittenDocumentsCarryAllThreeVersionFields() {
        val exempt = CRYPTO_PENDING_TASK_141 + VERSION_MIRROR_WRITERS
        val violations = productionSources()
            .filterNot { f -> exempt.any { f.unixPath().contains(it) } }
            .filter { SCHEMA_VERSION_WRITE.containsMatchIn(it.readText()) }
            .filter { file ->
                val text = file.readText()
                VERSION_FIELDS.drop(1).any { !text.contains(""""$it"""") }
            }
            .map { "${it.relativePath()} — writes schemaVersion without the other two" }
        report(
            violations,
            "HandWrittenHeaderComplete",
            "A document assembled by hand (a Firestore map, a buildJsonObject) must write all " +
                "three version fields, not just schemaVersion. Nothing type-checks these — which " +
                "is why the sign-in documents kept writing the pre-conversion shape through the " +
                "whole of TASK-138 while the security rules had already moved on, and account " +
                "creation was rejected at write time with every test still green.",
        )
    }

    @Test
    fun noIntegerVersionReachesTheWire() {
        val violations = productionSources().flatMap { file ->
            file.matches(INTEGER_VERSION_ON_WIRE) { it.trim() }
        }
        report(
            violations,
            "NoIntegerVersionOnTheWire",
            "Versions travel as dotted strings (\"1.0\"), never bare integers — wire-format.md " +
                "§2. An integer also silently inverts every comparison in firestore.rules, which " +
                "compares strings and would order \"10.0\" below \"9.0\".",
        )
    }

    @Test
    fun kotlinAndTypeScriptAgreeOnThePushWireVersion() {
        val kotlin = File(repoRoot, KOTLIN_PUSH_VERSIONS)
        val typescript = File(repoRoot, TYPESCRIPT_PUSH_VERSIONS)
        // Fail rather than pass when a side cannot be read. A comparison that quietly finds
        // nothing to compare is the failure mode this whole task exists to remove.
        check(kotlin.isFile) { "Cross-language check cannot find $KOTLIN_PUSH_VERSIONS" }
        check(typescript.isFile) { "Cross-language check cannot find $TYPESCRIPT_PUSH_VERSIONS" }

        val kotlinText = kotlin.readText()
        val typescriptText = typescript.readText()
        val violations = VERSION_CONSTANTS.mapNotNull { constant ->
            val fromKotlin = KOTLIN_VERSION_CONSTANT(constant).find(kotlinText)
                ?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }
                ?: return@mapNotNull "$constant — not found in $KOTLIN_PUSH_VERSIONS"
            val fromTypeScript = TYPESCRIPT_VERSION_CONSTANT(constant).find(typescriptText)
                ?.groupValues?.get(1)
                ?: return@mapNotNull "$constant — not found in $TYPESCRIPT_PUSH_VERSIONS"
            if (fromKotlin == fromTypeScript) null
            else "$constant — Kotlin says \"$fromKotlin\", TypeScript says \"$fromTypeScript\""
        }
        report(
            violations,
            "PushWireVersionCrossLanguage",
            "The client and the Worker speak one protocol and must agree on its version. They " +
                "hold independent constants, so a bump on one side alone is invisible until a " +
                "device talks to a Worker that refuses it. The comment in WireFormatVersion.kt " +
                "used to claim a 'T402 fitness function' checked this; that script was never " +
                "committed, and nothing checked it from spec 019 until now.",
        )
    }

    @Test
    fun everyWireFormatHasDeclaredRoundtripCoverage() {
        val declared = ROUNDTRIP_COVERAGE.keys
        val found = productionSources().flatMap { it.wireFormatDeclarations() }.toSet()
        check(found.size >= 15) {
            "Only ${found.size} wire formats detected — the parser is not matching the codebase, " +
                "so this rule would pass vacuously. Found: ${found.sorted()}"
        }

        val undeclared = (found - declared).map {
            "$it — implements WireVersionHeader but declares no roundtrip test"
        }
        val stale = declared.filterNot { it in found }.map {
            "$it — listed in ROUNDTRIP_COVERAGE but no longer a wire format"
        }
        val missingTests = ROUNDTRIP_COVERAGE.entries
            .filter { (_, test) -> testSources().none { it.name == test } }
            .map { (format, test) -> "$format — declared test file $test does not exist" }

        report(
            undeclared + stale + missingTests,
            "WireFormatRoundtripCoverage",
            "Every wire format needs a roundtrip test (wire-format.md §11) — write → read → " +
                "assert equal — and the test must be named here so the pairing is checked rather " +
                "than assumed. The machine finds the formats; this map is the human claim about " +
                "which test covers each. A name-matching heuristic was tried first and produced " +
                "false positives in both directions, which is worse than no rule: it reports " +
                "coverage that does not exist.",
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

    /**
     * Production Kotlin sources. Started as the modules `detektFoundation` covered; `core/push`
     * and `core/wire` were added in TASK-142 because they carry wire formats of their own and
     * were invisible to every rule here — `PushTriggerRequest` went undetected until the
     * coverage rule reported it as a stale entry rather than a missing format.
     */
    private fun productionSources(): List<File> =
        listOf("core", "app", "core/push", "core/wire")
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

    /**
     * Each `@Serializable` declaration in the file as `name to body`, where the body runs to the
     * next `@Serializable` or end of file. Per-declaration rather than per-file so that a file
     * holding several types reports the offending one by name.
     */
    private fun File.serializableDeclarations(): List<Pair<String, String>> {
        val text = readText()
        return SERIALIZABLE_DECLARATION.findAll(text).map { match ->
            val next = text.indexOf("@Serializable", match.range.last + 1)
            val body = text.substring(match.range.last + 1, if (next > 0) next else text.length)
            match.groupValues[1] to body
        }.toList()
    }

    /** Test sources of the modules this scan covers — used to check a declared test exists. */
    private fun testSources(): List<File> =
        listOf("core", "app", "core/push")
            .map { File(repoRoot, "$it/src") }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && it.extension == "kt" }
            .filter { f -> TEST_PATH_MARKERS.any { f.unixPath().contains(it) } }

    /**
     * Names of the types in this file that implement `WireVersionHeader`.
     *
     * Walks the constructor's parentheses rather than pattern-matching across them. A regex
     * spanning `class X(...) : WireVersionHeader` reaches past the closing paren into the NEXT
     * declaration's supertype list — it reported nested value types as formats, and because
     * matches do not overlap it also swallowed the text of real formats and hid them. Pool,
     * Preset and Profile all disappeared that way.
     */
    private fun File.wireFormatDeclarations(): List<String> {
        val text = readText()
        if (text.contains("interface WireVersionHeader")) return emptyList()
        return CLASS_HEADER.findAll(text).mapNotNull { match ->
            var index = match.range.last
            var depth = 0
            while (index < text.length) {
                when (text[index]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) break
                }
                index++
            }
            if (index >= text.length) return@mapNotNull null
            val supertypes = text.substring(index + 1, minOf(text.length, index + 200))
                .substringBefore('{')
            if (supertypes.contains(HEADER_INTERFACE)) match.groupValues[1] else null
        }.toList()
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

        // --- wire-format strict mode ---------------------------------------

        const val HEADER_INTERFACE = "WireVersionHeader"

        val VERSION_FIELDS = listOf("schemaVersion", "minReaderVersion", "minWriterVersion")

        val SERIALIZABLE_DECLARATION = Regex("""@Serializable[\s\S]{0,400}?\b(?:data\s+)?class\s+(\w+)""")

        /**
         * A declared version property. Matches the `override val` on a format and the plain
         * `val` on a type that should have been one — which is the case this rule exists to
         * catch.
         */
        val VERSION_PROPERTY = Regex("""\b(?:override\s+)?val\s+schemaVersion\s*:""")

        /**
         * An integer written to a version key: `"schemaVersion" to 1`, `"schemaVersion": 1`,
         * `put("schemaVersion", JsonPrimitive(1))`. A reference to a constant or a `.toString()`
         * does not match — those are the correct shapes.
         */
        val INTEGER_VERSION_ON_WIRE = Regex(
            """"(?:schemaVersion|minReaderVersion|minWriterVersion)"\s*(?:to|:|,)\s*""" +
                """(?:JsonPrimitive\()?\s*\d""",
        )

        /**
         * Crypto formats still carry the integer version by design: moving version handling out
         * of the crypto modules is TASK-141, deliberately split off so that `:core:crypto` and
         * `:core:keys` stay extractable without a dependency on the versioning module. Listed
         * explicitly rather than skipped silently — when TASK-141 lands this list goes away, and
         * an empty list is the signal that it did.
         */
        val CRYPTO_PENDING_TASK_141 = listOf(
            "adapters/crypto/FirestoreDeviceIdentityRepository.kt",
            "data/envelope/FirestoreEnvelopeStorage.kt",
            "data/envelope/FirestorePublicKeyDirectory.kt",
            "data/recovery/FirestoreRecoveryKeyBackup.kt",
        )

        /**
         * Writing a version key, as opposed to reading one. `"schemaVersion" to x`,
         * `["schemaVersion"] = x`, `put("schemaVersion", x)`. A reader — `data["schemaVersion"]`,
         * `getString("schemaVersion")` — does not match, which matters because most files
         * mentioning the key only read it.
         */
        val SCHEMA_VERSION_WRITE = Regex(
            """"schemaVersion"\s+to\b|\["schemaVersion"\]\s*=|put\(\s*"schemaVersion"""",
        )

        /**
         * Adapters that mirror the version at the document root for routing, while the real
         * three-field header travels inside the body. Requiring all three here would duplicate
         * the header rather than complete it. Both are generic document writers that receive one
         * `WireVersion` through `RemoteSyncBackend`, so they could not write the other two even
         * if we wanted them to — the port does not carry them.
         */
        /**
         * A constructor-parameter boundary: a comma or open paren that ends its line. Annotation
         * parentheses (`@EncodeDefault(EncodeDefault.Mode.ALWAYS)`) are followed by more text on
         * the same line, so they do not match.
         */
        val PARAMETER_SEPARATOR = Regex("""[,(][ \t]*\r?\n""")

        /** `class Name(` — the start of a declaration whose supertypes are examined by hand. */
        val CLASS_HEADER = Regex("""\bclass\s+(\w+)\s*\(""")

        /**
         * Which test proves each format survives a write → read → compare cycle
         * (`wire-format.md` §11). The machine finds the formats; this map is the claim about
         * coverage, checked in both directions — a new format missing here fails the build, and
         * so does an entry naming a test that no longer exists.
         *
         * Deliberately a declaration rather than a name-matching heuristic. The heuristic was
         * written first and got both directions wrong: it credited `Capability` to an unrelated
         * base64 test that merely mentioned the word, and reported `SessionRecord` as uncovered
         * while `SessionRecordRoundtripTest` sat right there.
         */
        val ROUNDTRIP_COVERAGE = mapOf(
            "Action" to "ActionWireFormatTest.kt",
            "Capability" to "CapabilityWireFormatTest.kt",
            "ConfigDocument" to "ConfigDocumentWireFormatTest.kt",
            "ConfigSnapshot" to "ConfigSnapshotRoundtripTest.kt",
            "Envelope" to "NamedConfigWireFormatTest.kt",
            "Health" to "HealthWireFormatTest.kt",
            "LauncherSettings" to "LauncherSettingsWireFormatTest.kt",
            "LinkBootstrap" to "LinkBootstrapWireFormatTest.kt",
            "NamedConfig" to "NamedConfigWireFormatTest.kt",
            "Pool" to "PoolSchemaV2RoundtripTest.kt",
            "Preset" to "PresetSchemaV2RoundtripTest.kt",
            "Profile" to "ProfileSchemaV2RoundtripTest.kt",
            "PushPayload" to "PushPayloadWireFormatTest.kt",
            "PushTriggerRequest" to "WireFormatRoundtripTest.kt",
            "SessionRecord" to "SessionRecordRoundtripTest.kt",
            "StateApplied" to "StateAppliedWireFormatTest.kt",
            "VendorRecipeCatalogue" to "VendorRecipeCatalogueWireFormatTest.kt",
        )

        // --- cross-language --------------------------------------------------

        const val KOTLIN_PUSH_VERSIONS =
            "core/push/src/commonMain/kotlin/family/push/api/WireFormatVersion.kt"
        const val TYPESCRIPT_PUSH_VERSIONS = "workers/push/src/contract/wire-format.ts"

        val VERSION_CONSTANTS = listOf("SCHEMA_VERSION", "MIN_READER_VERSION", "MIN_WRITER_VERSION")

        /** `val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)` → captures 1 and 0. */
        val KOTLIN_VERSION_CONSTANT: (String) -> Regex = { name ->
            Regex("""val\s+$name\s*:\s*WireVersion\s*=\s*WireVersion\(\s*(\d+)\s*,\s*(\d+)\s*\)""")
        }

        /** `export const SCHEMA_VERSION = "1.0";` → captures 1.0. */
        val TYPESCRIPT_VERSION_CONSTANT: (String) -> Regex = { name ->
            Regex("""export\s+const\s+$name\s*=\s*"([^"]+)"""")
        }

        val VERSION_MIRROR_WRITERS = listOf(
            "adapters/sync/FirebaseRemoteSyncBackend.kt",
            "adapters/sync/FirebaseTransactionScope.kt",
        )
    }
}
