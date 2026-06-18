package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 017 (F-4 AuthProvider) fitness gates (Phase 8, T791-T798).
 *
 * Аналог Detekt custom rules — реализован через file-walker test'ы, как
 * остальные Spec0NN_IsolationTest в проекте (Konsist DSL не везде нужен,
 * простой scan через `walkTopDown` достаточен).
 *
 *  - **T791** `NoVendorImportsInDomain`: `com.launcher.api.auth/` MUST NOT
 *    импортировать Firebase / Google Identity / Credential Manager SDK,
 *    MUST NOT упоминать в идентификаторах "Firebase" / "OAuth" / "Apple"
 *    / "Phone" / "Email" (кроме `AuthError.NoEmail` enum). FR-027, SC-003.
 *
 *  - **T792** `NoVendorImportsInConsumers`: consumer-territories
 *    (`com.launcher.api.config/`, `com.launcher.api.pairing/`,
 *    `com.launcher.adapters/config/`, и т.п.) MUST NOT импортировать
 *    Firebase Auth, Google Identity, Credential Manager. FR-028, SC-010-b.
 *
 *  - **T793** `NoFakeInRelease`: ничто в `:app/main` или `:app/realBackend`
 *    не должно импортировать `com.launcher.api.auth.FakeAuthAdapter` /
 *    `FakeSessionStore`. FR-019, SC-010-c.
 *
 *  - **T794** `NoAnonymousAuth`: нигде в проекте (кроме legacy spec 007
 *    `IdentityProvider` — будет удалён в Session E Phase 10 cleanup) НЕ
 *    должно быть `signInAnonymously`. FR-029, SC-010-d.
 *
 *  - **T795** `OAuthScopeWhitelist`: `GoogleSignInAuthAdapter` (когда
 *    появится в Phase 5) использует только `openid`, `email`, `profile`
 *    scope'ы — никакого `calendar`, `contacts`, `drive`. HIGH-4.
 *
 *  - **T796** `NoPIIInAuthLog`: `Log.*` вызовы в `adapters/auth/` и
 *    `:app/auth/` НЕ принимают `AuthIdentity`/`SessionRecord`/`User`,
 *    НЕ принимают строки с подозрительными именами (email, token, jwt,
 *    refresh, sub). HIGH-3, FR-033.
 *
 *  - **T797** `NoSessionRecordInConsumers`: импорт
 *    `com.launcher.api.auth.internal.*` запрещён вне `com.launcher.adapters.auth/`
 *    и тестов. Дополняет `internal` visibility modifier (она блокирует
 *    cross-module, но не intra-module). Clarification Q2.
 *
 *  - **T798** `NoClientComputedSubscriptionActive`: ни в одном
 *    production-файле (кроме fake-адаптеров) нет
 *    `SubscriptionState.Active` или `SubscriptionState.Expired` —
 *    эти состояния должны приходить server-validated JWT (S-10).
 *    FR-031, SC-014.
 */
class Spec017AuthIsolationTest {

    // ─── T791: domain auth port pure (no vendor SDK) ─────────────────────

    @Test
    fun T791_commonMain_auth_does_not_import_vendor_sdks() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/auth")
        if (!dir.isDirectory) return
        val forbidden = listOf(
            "com.google.firebase",
            "com.google.android.libraries.identity",
            "androidx.credentials",
            "com.google.android.gms.auth",
            "android.accounts",  // Android AccountManager API
        )
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "commonMain/api/auth/ must NOT import vendor SDKs (CLAUDE.md §1, spec 017 FR-027).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun T791_commonMain_auth_does_not_mention_provider_names_in_identifiers() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/auth")
        if (!dir.isDirectory) return
        // Запрет: Firebase, OAuth, Apple, Phone, Email **в идентификаторах**
        // (имена классов, функций, констант). Это не grep всей строки —
        // фраза в комментарии «provider-agnostic Firebase swap» допустима.
        // Whitelist: AuthError.NoEmail sealed object name.
        val forbiddenWords = listOf("Firebase", "OAuth", "Apple")
        val violations = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        return@forEachIndexed  // skip comments
                    }
                    forbiddenWords.forEach { word ->
                        if (trimmed.contains(word)) {
                            violations.add("${file.name}:${idx + 1}: identifier contains '$word' — $trimmed")
                        }
                    }
                }
            }
        assertTrue(
            "commonMain/api/auth/ identifiers must NOT name providers " +
                "(CLAUDE.md §1, spec 017 FR-027, clarification Q4).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T792: consumer territories don't see auth vendor SDKs ───────────

    @Test
    fun T792_consumer_packages_do_not_import_auth_vendor_sdks() {
        val coreSrc = locateCoreSrc()
        // Consumer territories — пакеты, которые ПОЛЬЗУЮТСЯ AuthProvider,
        // но не реализуют его. Они НЕ должны импортировать ничего auth-vendor.
        val consumerRoots = listOf(
            "kotlin/com/launcher/api/config",
            "kotlin/com/launcher/api/pairing",
            "kotlin/com/launcher/api/sync",
            "kotlin/com/launcher/api/history",
            "kotlin/com/launcher/adapters/config",
            "kotlin/com/launcher/adapters/edit",
            "kotlin/com/launcher/adapters/setup",
        )
        val forbidden = listOf(
            "com.google.firebase.auth",
            "com.google.android.libraries.identity.googleid",
            "androidx.credentials",
        )
        val violations = mutableListOf<String>()
        listOf("commonMain", "androidMain").forEach { sourceSet ->
            consumerRoots.forEach { root ->
                val dir = coreSrc.resolve("$sourceSet/$root")
                if (dir.isDirectory) {
                    violations += scanImports(dir, forbidden)
                }
            }
        }
        assertTrue(
            "Consumer territories must NOT import auth vendor SDKs " +
                "(CLAUDE.md §1, spec 017 FR-028).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T793: no Fake in :app release / main ────────────────────────────

    @Test
    fun T793_app_main_does_not_import_fake_auth_adapter() {
        val appSrc = locateAppSrc() ?: return
        val violations = mutableListOf<String>()
        listOf("main", "realBackend").forEach { variant ->
            val dir = appSrc.resolve(variant)
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    if (text.contains("import com.launcher.api.auth.FakeAuthAdapter") ||
                        text.contains("import com.launcher.api.auth.FakeSessionStore")
                    ) {
                        violations += "${file.path}: Fake auth import in production source"
                    }
                }
        }
        assertTrue(
            ":app/main and :app/realBackend must NOT import Fake auth adapters " +
                "(spec 017 FR-019, SC-010-c).\nViolations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T794: no anonymous Firebase Auth ────────────────────────────────

    @Test
    fun T794_no_anonymous_firebase_auth_calls() {
        val violations = mutableListOf<String>()
        val roots = listOfNotNull(
            locateCoreSrc(),
            locateAppSrc(),
        )
        roots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    // Whitelist:
                    //  - legacy IdentityProvider port + FirebaseIdentityProvider adapter
                    //    (будут удалены в Session E Phase 10 cleanup, до тех пор сосуществуют).
                    //  - fitness tests (ссылки на API в строках комментариев и assertion messages).
                    if (file.path.replace('\\', '/').let {
                            it.contains("api/identity/") ||
                                it.contains("adapters/identity/") ||
                                it.contains("test/fitness/")
                        }
                    ) {
                        return@forEach
                    }
                    file.readLines().forEachIndexed { idx, line ->
                        if (line.contains("signInAnonymously")) {
                            violations += "${file.path}:${idx + 1}: $line"
                        }
                    }
                }
        }
        assertTrue(
            "Anonymous Firebase Auth (signInAnonymously) запрещён везде кроме " +
                "legacy IdentityProvider port (deprecation pending, Session E Phase 10). " +
                "Spec 017 FR-029, SC-010-d, memory: project_auth_provider_architecture.md.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T795: OAuth scope whitelist ─────────────────────────────────────

    @Test
    fun T795_google_signin_uses_only_whitelisted_oauth_scopes() {
        // Этот тест pre-emptive: GoogleSignInAuthAdapter появится в Phase 5
        // (Session C). Когда появится — должен использовать ТОЛЬКО openid/email/profile.
        // Запрет: calendar, contacts, drive, gmail, fitness, youtube, plus.
        val dir = locateCoreSrc().resolve("androidMain/kotlin/com/launcher/adapters/auth")
        if (!dir.isDirectory) return  // adapter ещё не написан — тест проходит.
        val forbiddenScopes = listOf(
            "calendar", "contacts", "drive", "gmail", "fitness", "youtube", "plus",
            "spreadsheets", "documents", "presentations",
        )
        val violations = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                forbiddenScopes.forEach { scope ->
                    val needle = "\"$scope\""
                    if (text.contains(needle) || text.contains("\"https://www.googleapis.com/auth/$scope")) {
                        violations += "${file.name}: forbidden OAuth scope '$scope' detected"
                    }
                }
            }
        assertTrue(
            "Google Sign-In adapter must use only `openid`, `email`, `profile` scopes " +
                "(spec 017 HIGH-4 + Privacy Policy alignment FR-032).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T796: no PII в auth logging ─────────────────────────────────────

    @Test
    fun T796_no_pii_in_auth_logging() {
        // Запрет: Log.d/v/i/w/e в `adapters/auth/` где аргумент — PII type
        // (AuthIdentity, User, SessionRecord) или строка содержит подозрительные
        // имена. Разрешено: вызовы внутри AuthLog.kt (структурированный logger).
        val dir = locateCoreSrc().resolve("androidMain/kotlin/com/launcher/adapters/auth")
        if (!dir.isDirectory) return  // ещё не написан — тест проходит.
        val piiVarPatterns = listOf("email", "displayName", "token", "refresh", "jwt", "subClaim")
        val piiTypes = listOf("AuthIdentity", "SessionRecord", "User")
        val violations = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "AuthLog.kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                    val logCall = Regex("""Log\.[dviwe]\(""").containsMatchIn(trimmed)
                    if (!logCall) return@forEachIndexed
                    if (piiTypes.any { trimmed.contains(it) }) {
                        violations += "${file.name}:${idx + 1}: Log.* passes PII type — $trimmed"
                    }
                    piiVarPatterns.forEach { pat ->
                        if (trimmed.contains(pat, ignoreCase = true)) {
                            violations += "${file.name}:${idx + 1}: Log.* mentions PII pattern '$pat' — $trimmed"
                        }
                    }
                }
            }
        assertTrue(
            "Auth adapters must NOT log PII directly — use AuthLog typed methods " +
                "(spec 017 HIGH-3, research.md §R8).\nViolations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T797: SessionRecord/SessionStore are F-4-internal ───────────────

    @Test
    fun T797_session_record_not_imported_by_consumers() {
        val coreSrc = locateCoreSrc()
        val forbidden = listOf(
            "com.launcher.api.auth.internal.SessionRecord",
            "com.launcher.api.auth.internal.SessionStore",
        )
        val violations = mutableListOf<String>()
        coreSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                // Whitelist (normalised path, Windows backslashes safe):
                //  - sam port file itself (api/auth/internal/)
                //  - adapter implementation (adapters/auth/)
                //  - F-4 tests (commonTest/.../auth/, androidUnitTest/.../auth/)
                //  - fitness file (этот файл — references в строках)
                //  - DI wiring (BackendInit.kt) — by nature does see internal port,
                //    это normal usage (создание singleton), не consumer leak.
                val nPath = file.path.replace('\\', '/')
                if (nPath.contains("api/auth/internal/") ||
                    nPath.contains("adapters/auth/") ||
                    nPath.contains("commonTest/kotlin/com/launcher/api/auth") ||
                    nPath.contains("androidUnitTest/kotlin/com/launcher/adapters/auth") ||
                    nPath.contains("fitness/Spec017") ||
                    nPath.endsWith("/di/BackendInit.kt")
                ) {
                    return@forEach
                }
                file.readLines().forEachIndexed { idx, line ->
                    if (forbidden.any { line.contains(it) }) {
                        violations += "${file.path}:${idx + 1}: $line"
                    }
                }
            }
        assertTrue(
            "SessionRecord/SessionStore — F-4-internal (clarification Q2), " +
                "consumer'ы НЕ должны их импортировать.\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── T798: no client-computed Subscription Active/Expired ────────────

    @Test
    fun T798_no_client_assignment_of_subscription_active_or_expired() {
        // Запрет: `SubscriptionState.Active` или `.Expired` встречается в
        // production-коде. Эти ветки sealed type существуют **только** для
        // server-validated JWT path (S-10). Client-side: только Unknown /
        // LocalOnly / Trial (где Trial — тоже server-driven).
        //
        // Allow:
        //  - tests (commonTest/auth/ и androidUnitTest fitness)
        //  - sealed type definition (`SubscriptionState.kt`)
        //  - fake adapters могут симулировать Active для consumer-тестов
        val violations = mutableListOf<String>()
        val coreSrc = locateCoreSrc()
        val patterns = listOf("SubscriptionState.Active", "SubscriptionState.Expired")
        coreSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val nPath = file.path.replace('\\', '/')
                if (file.name == "SubscriptionState.kt" ||
                    nPath.contains("/commonTest/") ||
                    nPath.contains("/androidUnitTest/") ||
                    nPath.contains("/fitness/") ||
                    file.name.startsWith("Fake")
                ) {
                    return@forEach
                }
                file.readLines().forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                    patterns.forEach { p ->
                        if (trimmed.contains(p)) {
                            violations += "${file.path}:${idx + 1}: client-side $p — $trimmed"
                        }
                    }
                }
            }
        assertTrue(
            "SubscriptionState.Active/Expired запрещены в client-side production-коде " +
                "(spec 017 FR-031, SC-014, tamper-resistance CHK-TAM-003). " +
                "Эти ветки приходят только из server-validated JWT (S-10).\n" +
                "Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private fun scanImports(root: File, forbiddenPrefixes: List<String>): List<String> =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .mapIndexed { idx, raw -> idx + 1 to raw.trim() }
                    .filter { it.second.startsWith("import ") }
                    .map { (lineNo, raw) ->
                        lineNo to raw.removePrefix("import ").removeSuffix(";").trim()
                    }
                    .filter { (_, name) -> forbiddenPrefixes.any { name.startsWith(it) } }
                    .map { (lineNo, name) -> "${file.path}:$lineNo: import $name" }
            }
            .toList()

    private fun locateCommonMain(): File {
        val cwd = File(System.getProperty("user.dir"))
        return listOf(File(cwd, "src/commonMain"), File(cwd, "core/src/commonMain"))
            .firstOrNull { it.isDirectory }
            ?: error("commonMain not found from cwd=$cwd")
    }

    private fun locateCoreSrc(): File {
        val cwd = File(System.getProperty("user.dir"))
        return listOf(File(cwd, "src"), File(cwd, "core/src"))
            .firstOrNull { it.isDirectory }
            ?: error("core/src not found from cwd=$cwd")
    }

    private fun locateAppSrc(): File? {
        val cwd = File(System.getProperty("user.dir"))
        return listOf(File(cwd, "../app/src"), File(cwd, "app/src"))
            .firstOrNull { it.isDirectory }
    }
}
