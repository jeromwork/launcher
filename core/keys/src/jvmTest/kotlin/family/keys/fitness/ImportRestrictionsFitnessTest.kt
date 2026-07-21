package family.keys.fitness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Phase 7 fitness functions — import-restriction enforcement (T120 + T121, CLAUDE.md rule 7).
 *
 * Detekt не настроен на проекте; вместо подключения Detekt-плагина (значительная
 * инфра — отдельная конфигурация, кастомные правила, CI wiring) используем
 * filesystem-walking JVM-тест. Подход эквивалентный: проверяем все .kt файлы
 * production-кода на forbidden `import` строки.
 *
 * **T120** — внутри `:core:keys` production-кода НЕ должно быть прямых
 * импортов libsodium (`com.ionspin.kotlin.crypto.*`). Domain ports + impl
 * работают через port [family.crypto.api.*] из `:core:crypto`, а libsodium
 * скрыт за `family.crypto.libsodium.*` adapter'ами. Тесты — exception
 * (bootstrap LibsodiumInitializer допустим, поскольку test fixture использует
 * real adapter).
 *
 * **T121** — Firebase SDK импорты (`com.google.firebase.*`,
 * `com.google.android.gms.tasks.*`) допустимы ТОЛЬКО в build variant'е
 * `app/src/realBackend/`. Variant-agnostic `app/src/main/` использует domain
 * ports из `:core:keys` + `:core:auth` — никакой Firebase-зависимости.
 * `mockBackend/` использует in-memory fakes. Domain `:core:keys/` тоже
 * полностью Firebase-free (CLAUDE.md rule 1 domain isolation).
 */
class ImportRestrictionsFitnessTest {

    private val repoRoot: File by lazy { findRepoRoot() }

    @Test
    fun coreKeysProductionMustNotImportIonspinCrypto() {
        val coreKeys = File(repoRoot, "core/keys/src")
        check(coreKeys.exists()) { "core/keys/src not found at ${coreKeys.absolutePath}" }

        // Only commonMain + androidMain (если появится) — production sources.
        // commonTest/jvmTest/androidInstrumentedTest скипаем, т.к. тестам разрешено
        // bootstrap'ить LibsodiumInitializer.
        val productionRoots = listOf("commonMain", "androidMain").map { File(coreKeys, it) }
            .filter { it.exists() }

        val violations = mutableListOf<Violation>()
        for (root in productionRoots) {
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    findForbiddenImport(file, FORBIDDEN_LIBSODIUM_IMPORT)?.let { violations.add(it) }
                }
        }
        assertNoViolations(
            violations,
            ruleName = "T120 — no libsodium imports in :core:keys production",
            hint = "Use family.crypto.api.* port instead; libsodium lives behind family.crypto.libsodium.*"
        )
    }

    @Test
    fun appMainAndCoreKeysMustNotImportFirebase() {
        val appMain = File(repoRoot, "app/src/main")
        val appMockBackend = File(repoRoot, "app/src/mockBackend")
        val coreKeysSrc = File(repoRoot, "core/keys/src")
        // realBackend — единственный whitelisted source set где Firebase разрешён.

        val violations = mutableListOf<Violation>()
        listOf(appMain, appMockBackend, coreKeysSrc)
            .filter { it.exists() }
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        findForbiddenImport(file, FORBIDDEN_FIREBASE_IMPORT)?.let { violations.add(it) }
                    }
            }
        assertNoViolations(
            violations,
            ruleName = "T121 — no Firebase imports outside app/src/realBackend",
            hint = "Move Firebase usage to app/src/realBackend/ ACL adapter or guard via RecoveryKeyBackup port."
        )
    }

    @Test
    fun appCodeOutsideAdaptersMustNotImportKeysApiInternal() {
        // family.keys.api.internal.* — это backend-adapter SPI: ConfigCipher2,
        // RecipientResolver, EnvelopeStorage, PublicKeyDirectory, DeviceIdentity.
        // Caller code должен использовать только family.keys.api.RemoteStorage и
        // прочие top-level public ports (без `internal` segment в пакете).
        // realBackend / mockBackend / тесты — единственные легитимные потребители.
        val appMain = File(repoRoot, "app/src/main")
        if (!appMain.exists()) return

        val violations = mutableListOf<Violation>()
        appMain.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                findForbiddenImport(file, FORBIDDEN_KEYS_INTERNAL_IMPORT)?.let { violations.add(it) }
            }
        assertNoViolations(
            violations,
            ruleName = "Encapsulation — no family.keys.api.internal in app/src/main",
            hint = "Use family.keys.api.RemoteStorage (public facade). " +
                "If you need a port from family.keys.api.internal, the adapter must live in app/src/realBackend (or mockBackend)."
        )
    }

    @Test
    fun realBackendIsTheOnlyFirebaseSurface() {
        // Sanity-check: убеждаемся что realBackend существует и содержит хотя бы
        // одну Firebase ссылку. Без этого правило T121 теряет смысл (false-negative
        // если фактически Firebase нигде не используется и кто-то перенесёт код).
        val realBackend = File(repoRoot, "app/src/realBackend")
        if (!realBackend.exists()) {
            // Нет realBackend variant'а — модуль ещё не включил Firebase. Skip.
            return
        }
        val hasFirebaseImport = realBackend.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .any { file -> findForbiddenImport(file, FORBIDDEN_FIREBASE_IMPORT) != null }
        assertTrue(
            hasFirebaseImport,
            "realBackend/ exists but не содержит Firebase imports — правило T121 окажется vacuous. " +
                "Проверьте: либо Firebase интеграция перенесена куда-то ещё (нарушение), либо " +
                "realBackend/ можно удалить."
        )
    }

    // ---- helpers ----

    private data class Violation(val file: File, val lineNumber: Int, val line: String) {
        override fun toString(): String = "${file.relativeTo(File(".").canonicalFile)}:$lineNumber  $line"
    }

    private fun findForbiddenImport(file: File, pattern: Regex): Violation? {
        // Read line-by-line to keep memory bounded (some generated test files могут быть большими).
        file.useLines { lines ->
            lines.forEachIndexed { idx, line ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith("import ") && pattern.containsMatchIn(trimmed)) {
                    return Violation(file, idx + 1, trimmed)
                }
            }
        }
        return null
    }

    private fun assertNoViolations(
        violations: List<Violation>,
        ruleName: String,
        hint: String
    ) {
        if (violations.isEmpty()) return
        val summary = buildString {
            appendLine("Fitness rule violated: $ruleName")
            appendLine(hint)
            appendLine("Violations (${violations.size}):")
            violations.forEach { appendLine("  • $it") }
        }
        fail(summary)
    }

    private fun findRepoRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists() || File(dir, "settings.gradle").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Could not find repo root (settings.gradle.kts) from ${File(".").canonicalPath}")
    }

    /**
     * SC-007: forbidden provider-identifying tokens in :core:keys/commonMain source code (T631).
     *
     * Grep all .kt files under `core/keys/src/commonMain/` for forbidden token patterns.
     * Violations in KDoc comment lines (starting with ` * ` or `//`) are excluded —
     * the rule targets production code tokens only (identifiers, string literals, type names).
     *
     * **Scope exclusions** (per inventory.md §D4):
     *  - `AAD_PREFIX = "f5-recovery-vault-v1"` — FR-018 byte-equal wire constant; intentional.
     *  - KDoc/comment text — excluded from scan.
     *  - `firestore.rules` — out of scope (deployment surface).
     */
    @Test
    fun coreKeysCommonMainMustNotContainForbiddenTokens() {
        val commonMain = File(repoRoot, "core/keys/src/commonMain")
        if (!commonMain.exists()) return  // not yet created — skip gracefully

        val violations = mutableListOf<Violation>()
        commonMain.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { idx, line ->
                        val trimmed = line.trimStart()
                        // Skip KDoc and comment lines.
                        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) return@forEachIndexed
                        if (FORBIDDEN_DOMAIN_TOKENS.containsMatchIn(line)) {
                            violations.add(Violation(file, idx + 1, line.trimEnd()))
                        }
                    }
                }
            }
        assertNoViolations(
            violations,
            ruleName = "SC-007 — no provider-identifying tokens in :core:keys/commonMain",
            hint = "Tokens Google|Firebase|OAuth|Apple|PhoneNumber|PhoneAuth|IdToken|Cloudflare|WorkerUrl|Email|Sub " +
                "MUST NOT appear in domain code. Move to adapter layer (app/src/realBackend or androidMain adapter). " +
                "Scope exclusions: AAD_PREFIX wire constant (FR-018), KDoc comments."
        )
    }

    companion object {
        /**
         * Любой импорт из `com.ionspin.kotlin.crypto.*` — корневой пакет libsodium
         * bindings. Сейчас в коде встречается `LibsodiumInitializer` —
         * test-only, в production должно быть 0 occurrences.
         */
        private val FORBIDDEN_LIBSODIUM_IMPORT = Regex("""com\.ionspin\.kotlin\.crypto\b""")

        /**
         * Firebase Android SDK (`com.google.firebase.*`) + Tasks API
         * (`com.google.android.gms.tasks.*`) — оба связаны с Firestore /
         * Auth / Cloud Messaging. Если что-то ещё попадёт в realBackend
         * (например, `com.google.android.gms.auth.*` для Sign-In) —
         * добавить отдельный pattern. Сейчас Sign-In лежит за F-4
         * port'ом, без прямой `com.google.android.gms.auth` зависимости.
         */
        private val FORBIDDEN_FIREBASE_IMPORT = Regex(
            """com\.google\.firebase\b|com\.google\.android\.gms\.tasks\b"""
        )

        /**
         * The keys-module SPI package: backend-adapter contracts (ConfigCipher2,
         * RecipientResolver, EnvelopeStorage, PublicKeyDirectory, DeviceIdentity).
         * App code outside realBackend / mockBackend must reach the keys layer via
         * family.keys.api.RemoteStorage facade, not via these internal contracts.
         */
        private val FORBIDDEN_KEYS_INTERNAL_IMPORT = Regex(
            """cryptokit\.keys\.api\.internal\b"""
        )

        /**
         * SC-007: forbidden provider-identifying tokens in domain code.
         * Matches identifier-boundary occurrences (\b) to avoid false positives
         * like 'GMS' inside 'ALGORITHMS' or 'apple' inside 'pineapple'.
         *
         * Exclusions: AAD_PREFIX string "f5-recovery-vault-v1" — wire constant;
         * KDoc/comment lines — excluded by calling code.
         * Note: Tokens Worker, Bearer, JWT, HTTP, OkHttp, R2, KV from handoff DZ-1 are
         * intentionally omitted to prevent false-positives (e.g. WorkManager Worker,
         * BearerToken in DI, generic HTTP/KV structures).
         */
        private val FORBIDDEN_DOMAIN_TOKENS = Regex(
            """\b(?:Google|Firebase|OAuth|Apple|PhoneNumber|PhoneAuth|IdToken|Cloudflare|WorkerUrl|Email|Sub)\b"""
        )
    }
}
