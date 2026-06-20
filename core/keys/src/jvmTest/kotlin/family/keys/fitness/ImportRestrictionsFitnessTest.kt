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
            hint = "Move Firebase usage to app/src/realBackend/ ACL adapter or guard via RecoveryKeyVault port."
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
    }
}
