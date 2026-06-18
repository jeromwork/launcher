# Research: F-4 — AuthProvider port + Google Sign-In adapter

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-06-18

Research-фаза разбирает архитектурные решения, требующие сравнения альтернатив (per CLAUDE.md rule 3). Каждый раздел: контекст, варианты, decision, обоснование, exit ramp.

---

## R1. ID Token `sub` claim extraction для identity-links lookup

**Контекст**: per FR-016a + clarification Q1, после успешного Google sign-in adapter должен извлечь `sub` claim из Google ID Token и сделать lookup в `/identity-links/google/{sub}` для получения нашего UUID. Вопрос: как технически extract'нуть `sub` без переписки в JWT parsing logic?

**Варианты**:

| Variant | Approach | Pros | Cons |
|---------|----------|------|------|
| **(a)** Credential Manager `GoogleIdTokenCredential.getIdToken()` → manual JWT parse через `kotlinx.serialization.json` (split by `.`, decode base64url middle, parse JSON, get `sub`) | Из Credential Manager → manual parse | No additional dependency; explicit; testable; we own the code | ~15 LOC parsing logic; need base64url decoder |
| **(b)** Использовать Firebase `FirebaseAuth.signInWithCredential().additionalUserInfo.profile["sub"]` после exchange | Firebase Auth handles parse | Less code; Firebase verifies signature | Cross-cuts Step 1 (Credential Manager) and Step 2 (Firebase exchange) — нельзя получить sub до Firebase call. Couples identity-links lookup к Firebase. Slower (extra network call before lookup). |
| **(c)** Использовать Google Auth library `GoogleIdTokenVerifier` | Google-blessed JWT parser + signature verification | Signature verified | New dependency. Heavyweight для extracting one field. Signature already verified by Credential Manager (Google API trust). |

**Decision: (a) — manual JWT payload decode**.

**Обоснование**:
- Signature verification уже сделана Credential Manager (Google API trust). Нам нужен **только** payload field `sub` — base64url decode + JSON parse достаточно.
- Не добавляет dependencies в `app/androidMain/auth/`.
- Позволяет identity-links lookup **до** Firebase exchange — Step 1.5: Credential Manager → extract sub → identity-links lookup → если новый → создать UUID → Firebase exchange (Step 2) → save SessionRecord. Это правильный flow: identity (UUID) известна до того как мы привязываемся к Firebase token.
- Variant (b) seemed «cleaner» но couples lookup к Firebase Auth — нарушает Step 1 / Step 2 separation декларированную в FR-005.
- Variant (c) — overkill для single field extraction.

**Exit ramp**: if base64url / JSON parse logic becomes buggy → switch to (c) с `GoogleIdTokenVerifier`. Trade-off: +1 dependency. ~30 минут работы. Wrapping behind private function `extractSubClaim(idToken: String): String` keeps switch local.

**Implementation note**: `extractSubClaim` сидит в `GoogleSignInAuthAdapter.kt` приватной функцией. Unit test: hardcoded ID Token fixture (Google sample) → expected `sub` value.

---

## R2. In-flight signIn coroutine scope (state-management HIGH-5)

**Контекст**: critical UX issue из state-management checklist. Сценарий:
1. User taps «Войти» в `SignInTrigger`.
2. Coroutine launched в composable scope → opens Google bottom-sheet.
3. User rotates device → Activity recreated → composable disposed → coroutine **cancelled**.
4. Google bottom-sheet still visible (Google handles its own visibility).
5. User selects account → Google returns credential → **nothing receives it** (original coroutine dead).
6. User видит «ничего не произошло» — silent fail.

**Варианты**:

| Variant | Approach | Pros | Cons |
|---------|----------|------|------|
| **(a)** Hoist coroutine scope к `AuthProvider` adapter (application-level singleton). Result delivered через `currentUser` flow, не direct callback. | Adapter owns lifecycle | Robust к UI lifecycle changes; new SignInTrigger composable после rotation просто observes `currentUser` flow и видит updated state; standard reactive pattern. | Adapter становится statefulness owner ("in-flight signIn"); need to handle cancellation если user явно navigates away. |
| **(b)** Cancel + auto-retry: bottom-sheet auto-closes при cancellation, user must re-tap «Войти» в новой composable. | Simple to implement | Bad UX — user не понимает почему bottom-sheet закрылся; повторный tap раздражает. |
| **(c)** Save state в `rememberSaveable` (in-flight signIn flag) и re-launch coroutine после rotation. | Composable-local solution | Complex; race conditions; не handles process death. |
| **(d)** `ViewModel`-scoped coroutine (survives configuration change). | Standard Android pattern | Only handles rotation, not process death. F-4 не использует ViewModel-based architecture (Compose-only). |

**Decision: (a) — Adapter-scoped coroutine**.

**Обоснование**:
- Reactive pattern уже встроен в F-4: `currentUser: Flow<AuthIdentity?>`. SignInTrigger подписан. Result of signIn эмитится через flow → new composable instance after rotation automatically renders updated state.
- Survives process death (если sign-in завершился до process kill — SessionStore сохранил blob; при cold-start restore restores identity).
- Никаких ViewModel.
- Adapter уже singleton (DI-injected per Application lifecycle).

**Implementation**:

В `GoogleSignInAuthAdapter`:
```
private val adapterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
private var inFlightSignIn: Job? = null

override suspend fun signIn(): Outcome<AuthIdentity, AuthError> {
    // Dedup: if already in-flight, await existing
    inFlightSignIn?.let { return suspendUntilComplete(it) }

    val deferred = adapterScope.async {
        // Steps 1.5 and 2: Credential Manager → sub extraction → identity-links lookup → Firebase exchange → SessionStore save → emit
    }
    inFlightSignIn = deferred
    return try {
        deferred.await()
    } finally {
        inFlightSignIn = null
    }
}
```

Caller (`SignInTrigger` composable) launches `signIn()` в LaunchedEffect tied к composable lifecycle. **Composable disposal cancels caller coroutine**, но adapter scope **продолжает** signIn. Result через `currentUser` flow доступен new SignInTrigger composable.

**Exit ramp**: if adapter-scoped logic становится too complex (например, multiple in-flight signIns для multi-provider) → migrate к dedicated `SignInCoordinator` class. Adapter stays simple. 1-2 days work.

---

## R3. Firebase Auth Emulator для CI integration testing

**Контекст**: instrumentation tests (`GoogleSignInAdapterInstrumentationTest`) требуют либо real Firebase project (admin credentials в CI — leak risk), либо local Firebase Auth Emulator (Google-published, runs locally / в CI Docker).

**Варианты**:

| Variant | Approach | Pros | Cons |
|---------|----------|------|------|
| **(a)** Firebase Emulator Suite (Auth + Firestore) в CI через Docker | Local, no real Firebase project | No credential leak risk; isolated; fast; supports identity-links Firestore testing; standard Google pattern | CI image needs Java + Firebase CLI installation; ~30s startup overhead per test run; some Firebase Auth flows (real Google credential exchange) не supported в emulator. |
| **(b)** Dedicated `qa` Firebase project с testing service account | Real Firebase behavior | All Firebase flows supported; closer to production | Credentials must be in CI secrets; network-dependent; slower; rate-limited. |
| **(c)** Skip integration tests в CI, run manually pre-release | Simple CI | Zero setup | Doesn't catch regressions; manual burden. |
| **(d)** Mock Firebase Auth + Firestore at Kotlin boundary (use `mockk`) | No emulator at all | Fast; isolated; reproducible | Doesn't test real Firebase SDK behavior; risk: API changes silently break our adapter. |

**Decision: (a) Firebase Emulator Suite + (d) mockk для unit tests**.

**Обоснование**:
- (a) для **instrumentation tests** — runs real Firebase Auth SDK against emulator, catches API drift.
- (d) для **JVM unit tests** — fast feedback loop in IDE, no Docker required for developer machine.
- (b) considered, rejected: credential management overhead для open-source dev workflow.

**Setup details**:
- `firebase.json` в repo root: configures Auth emulator (port 9099) + Firestore emulator (port 8080).
- CI workflow (`.github/workflows/android-tests.yml` или similar): step «start emulators» before instrumentation tests.
- Test runner uses `FirebaseEmulatorRule` (JUnit rule, custom) — points Firebase SDK к localhost emulator endpoints.
- Test data: pre-seeded test users в emulator (`test-admin@example.com`, `test-senior@example.com`) с hardcoded UIDs.

**Exit ramp**: if Firebase Emulator becomes too flaky (известно бывает на Windows) → switch к (b) с CI-secret-stored testing project credentials. Trade-off: credentials rotation overhead.

**Documentation**: full setup в `quickstart.md` § "Firebase Emulator Setup".

---

## R4. Identity-links Firestore migration to own-server

**Контекст**: per clarification Q1 + FR-016a + server-roadmap SRV-AUTH-IDENTITY-001 — identity-links collection **первой** мигрирует на own-server. Research-фаза должна подтвердить что migration plan technically feasible **до** start кода F-4 (если nope — переоценить design).

**Стратегия cutover** (high-level):

1. **Phase 1 — own-server stands up identity-links endpoint** parallel к Firestore (read-only mirror, sync через Firestore Trigger или batch copy).
2. **Phase 2 — dual-write**: новые sign-ins пишут в обе системы. Existing reads — из Firestore (source of truth).
3. **Phase 3 — switch read source**: app version N+1 читает из own-server. Firestore identity-links становится backup.
4. **Phase 4 — stop Firestore writes**: app version N+2 пишет только в own-server.
5. **Phase 5 — Firestore collection archived / deleted** (после grace period N+3).

**Key question**: что должен делать F-4 code сегодня, чтобы Phase 1-5 был **additive** (не rewrite)?

**Answer**:
- `GoogleSignInAuthAdapter` имеет **private function** `lookupOrCreateIdentityLink(sub: String): String` (returns stableId UUID). Today: implementation calls Firestore. После cutover Phase 3: implementation calls own-server endpoint. **Function signature не меняется**. Consumer code (rest of adapter) не меняется.
- `IdentityLinksClient` interface (introduced if Phase 3+ нужен switch без recompile) wraps lookup. Today **inline** в adapter; if cutover requires runtime switch — extract как separate port. Inline TODO ниже.

**Inline TODO** в `GoogleSignInAuthAdapter`:
```kotlin
// TODO(server-roadmap SRV-AUTH-IDENTITY-001): identity-links lookup currently
// hits Firestore directly. After own-server cutover Phase 3, replace this
// function body with own-server endpoint call. Function signature stays.
// Per research.md §R4 and decision 2026-06-18.
private suspend fun lookupOrCreateIdentityLink(sub: String): String { ... }
```

**Data migration**: Firestore → own-server requires:
- Export `/identity-links/{provider}/{accountId}` documents (bulk read API).
- Transform: Firestore document shape → own-server table row.
- Import: bulk insert в own-server DB.
- Verify: random-sample check `accountId → stableId` mapping identical.

**Estimated effort при cutover** (для context, не F-4 task): 1 week dev + 1 day operational migration window.

**Decision**: design **feasible**. F-4 implementation proceeds. SRV-AUTH-IDENTITY-001 entry в server-roadmap (HIGH item для plan stage).

---

## R5. Wire format choice: JSON vs Protobuf vs CBOR для SessionRecord

**Контекст**: SessionRecord blob serialized для EncryptedSharedPreferences. Choice влияет на FR-021 (currently JSON via `kotlinx.serialization.json`).

**Варианты**:

| Format | Pros | Cons |
|--------|------|------|
| **JSON** (`kotlinx.serialization.json`) | Human-readable (debug); standard tooling; existing project dependency; same as F-CRYPTO KeyBlob choice | Slightly larger size; not significant для <1KB blobs |
| **Protobuf** | Compact; schema-versioned natively | New dependency; tooling; overkill для single internal blob |
| **CBOR** (`kotlinx.serialization.cbor`) | Compact binary; same Kotlin serialization | Less debug-friendly; no project precedent |

**Decision: JSON**.

**Обоснование**:
- Consistency с F-CRYPTO (spec 016 §FR-016 decision Q5): «JSON для KeyBlob'ов их единицы, размер не важен, debug-ability ценнее».
- SessionRecord blob ~200-300 bytes — size negligible.
- Encrypted at rest (EncryptedSharedPreferences), readable plaintext только в memory во время usage.
- Backward-compat migration через `schemaVersion` bump + migrator (same pattern как F-CRYPTO).

**Exit ramp**: if в будущем session blob grows large (например, multi-provider state) → migrate к CBOR через `schemaVersion: 2`. ~1 day work (migrator + tests). Per CLAUDE.md rule 5 wire format migration.

---

## R6. `androidx.credentials` library version + minSdk floor

**Контекст**: plan.md заявляет minSdk = 24. Credential Manager библиотека `androidx.credentials:credentials` supports varied API levels через backport. Нужно подтвердить.

**Findings** (cross-referenced с AndroidX official docs):
- `androidx.credentials:credentials:1.3.x` — supports API 19+. Google Identity provider (`credentials-play-services-auth`) — supports API 23+.
- Project minSdk = 24 (per F-CRYPTO spec). Safe margin.
- Credential Manager + Google provider требуют Google Play Services version ≥ 16.0.0 (very old, present на всех modern devices). `AuthAdapterSelector` (FR-018) detects через `GoogleApiAvailability.isGooglePlayServicesAvailable()`.

**Decision**: minSdk = 24 confirmed. Lock library version в `version-catalog.toml` (`libs.versions.toml`):
```toml
androidx-credentials = "1.3.0"  # confirmed at research time; bump per release notes
google-id = "1.1.1"
```

**Exit ramp**: if AndroidX bumps minSdk floor → reevaluate. Currently no such trajectory visible.

---

## R7. SignInTrigger composable — состояния и transition policy

**Контекст**: ux-quality + elderly-friendly + state-management open items требуют explicit definition UI states `SignInTrigger`.

**Decision — 5 explicit states**:

| State | Trigger | UI shown | Transitions |
|-------|---------|----------|-------------|
| **NotSignedIn** | `currentUser == null` initial | Кнопка «Войти в аккаунт» + поясняющий текст «Чтобы настройки сохранялись и были доступны на других устройствах» | tap → `Loading.SigningIn` |
| **Loading.SigningIn** | User tapped «Войти», `signIn()` in flight | Same кнопка + progress indicator overlay; кнопка disabled | success → `SignedIn`; cancelled/error → `NotSignedIn` (error state ниже) |
| **SignedIn** | `currentUser != null` | «Вошли как `<email>`» + кнопка «Выйти» | tap «Выйти» → `Loading.SigningOut` |
| **Loading.SigningOut** | User tapped «Выйти», `signOut()` in flight | Кнопка disabled + progress indicator | complete → `NotSignedIn` |
| **Error** (NoEmail / NetworkError / ProviderUnavailable) | `signIn()` returned Failure (не Cancelled) | Прежний NotSignedIn UI + inline error message ниже кнопки («используйте личный Google-аккаунт» / «нет соединения» / «вход через Google недоступен на этом устройстве») | tap «Войти» → `Loading.SigningIn` (retry); error message dismisses |

**Cancelled** не отдельное state — переход на `NotSignedIn` без error message (per clarification Q6 «никаких toast'ов»).

**Implementation pattern**:
```kotlin
sealed class SignInTriggerState {
    object NotSignedIn : SignInTriggerState()
    object SigningIn : SignInTriggerState()
    data class SignedIn(val identity: AuthIdentity) : SignInTriggerState()
    object SigningOut : SignInTriggerState()
    data class Error(val reason: AuthError) : SignInTriggerState()
}
```

State derivation: from `currentUser: Flow<AuthIdentity?>` + local `rememberSaveable<Boolean>("inFlight")` + local `remember<AuthError?>("lastError")`. **NB**: `inFlight` survives rotation through `rememberSaveable`; `lastError` ephemeral (cleared on next user action). Per state-management CHK009.

**Error inline display**: small Text composable ниже button, red color + warning icon (per elderly-friendly CHK018 — icon + text, не только color).

**Recreation behavior**:
- `NotSignedIn` / `SignedIn` derived from currentUser flow → automatic.
- `SigningIn` survives через `rememberSaveable` + adapter-scoped coroutine (R2 above).
- `Error` lost on recreation (acceptable — user re-taps «Войти» и видит fresh error if still happens).

---

## R8. Logging policy — categories + Detekt enforcement

**Контекст**: HIGH-3 logging policy + Detekt rule `NoPIIInAuthLog`.

**Categories** (структурированные tag + parameters, **никакого** PII):

| Category | When | Allowed fields | Example |
|----------|------|----------------|---------|
| `Auth.SignIn.Attempt` | `signIn()` called | nothing (или providerKind после future Phone adapter — но даже это metadata, не PII) | `[Auth] sign_in.attempt` |
| `Auth.SignIn.Success` | currentUser emits non-null после signIn | `presence flags`: `emailPresent=true/false`, `displayNamePresent=true/false` — НЕ значения | `[Auth] sign_in.success emailPresent=true displayNamePresent=true` |
| `Auth.SignIn.Failure` | signIn returns Failure | `reason` enum value (NoEmail / NetworkError / ProviderUnavailable / Cancelled / Unknown) | `[Auth] sign_in.failure reason=NoEmail` |
| `Auth.TokenRefresh.Attempt` | adapter triggers refresh | nothing | `[Auth] token_refresh.attempt` |
| `Auth.TokenRefresh.Success` | refresh завершился успешно | nothing | `[Auth] token_refresh.success` |
| `Auth.TokenRefresh.Failure` | refresh failed | `reason` enum | `[Auth] token_refresh.failure reason=NetworkError` |
| `Auth.Session.Restored` | cold-start session blob successfully restored | `presence` (identityPresent=true) | `[Auth] session.restored identityPresent=true` |
| `Auth.Session.Corrupted` | corrupted session blob detected | nothing | `[Auth] session.corrupted` |
| `Auth.IdentityLinks.Lookup` | identity-links Firestore lookup | `outcome` (found / created / failed) | `[Auth] identity_links.lookup outcome=created` |
| `Auth.IdentityLinks.Failure` | lookup network/permission error | `reason` enum | `[Auth] identity_links.lookup outcome=failed reason=NetworkError` |

**Detekt rule `NoPIIInAuthLog`** (custom):
- Scans `auth/` package для `android.util.Log.*` calls (`Log.d`, `Log.i`, `Log.w`, `Log.e`).
- Checks parameter expressions: if any parameter type — `AuthIdentity`, `SessionRecord`, `User`, или name содержит `email`, `displayName`, `token`, `refresh`, `sub` → FAIL.
- Allowed: String constants, enum values, boolean presence flags, integer counts.

**Implementation**: `AuthLog.kt` exposes typed methods:
```kotlin
object AuthLog {
    fun signInAttempt() { Log.i("Auth", "sign_in.attempt") }
    fun signInSuccess(emailPresent: Boolean, displayNamePresent: Boolean) { ... }
    fun signInFailure(reason: AuthError) { ... }
    // ... etc
}
```

Adapter code calls `AuthLog.signInAttempt()`, не raw `Log.d`. Detekt rule allows direct `Log.*` calls **only** if file is `AuthLog.kt`; elsewhere — only `AuthLog.X(...)` methods.

**Crash reports** (Crashlytics): same policy. Custom Crashlytics keys (`auth.last_error_reason`) — enum values, not PII.

---

## R9. Firestore Security Rules — atomic create-only для identity-links

**Контекст**: HIGH-2 + tamper-resistance + failure-recovery CHK009 (identity-links race / spoofing).

**Required invariants**:
1. **Read**: only authenticated user может read `/identity-links/google/{sub}` if their Firebase auth UID == sub (their own).
2. **Create**: only if document does NOT exist AND auth UID == sub. **No overwrites** (prevents spoofing).
3. **Update**: forbidden. **Delete**: forbidden (только для S-6 Account Deletion через Cloud Function — out of F-4 scope).
4. **Race protection**: dual sign-in одного аккаунта на двух devices одновременно → first write wins, second получает `permission-denied` → retry с read (now finds existing UUID).

**Firestore Rules** (плановые, full text в `contracts/firestore-security-rules.md`):

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // identity-links: providerKind = "google" subpath
    match /identity-links/google/{providerAccountId} {
      allow read: if request.auth != null
                  && request.auth.uid == providerAccountId;
      allow create: if request.auth != null
                    && request.auth.uid == providerAccountId
                    && !exists(/databases/$(database)/documents/identity-links/google/$(providerAccountId))
                    && request.resource.data.keys().hasOnly(['schemaVersion', 'stableId', 'createdAt'])
                    && request.resource.data.schemaVersion == 1
                    && request.resource.data.stableId is string
                    && request.resource.data.stableId.size() == 36;  // UUID v4 length
      allow update, delete: if false;  // immutable
    }

    // users: keyed by our stableId
    match /users/{stableId} {
      // F-4 only creates empty doc; consumer-specs (F-5, S-8) add fields with their own rules.
      // F-4 invariant: doc creation only by the owner during identity-links creation transaction.
      // Full rules для users — будут в S-8 spec.
      allow read: if request.auth != null && /* matches the identity-link's owner */ true;  // placeholder, full rules в S-8
      allow create: if /* see Firestore transaction в GoogleSignInAuthAdapter */ true;
      allow update, delete: if false;  // for F-4 phase only
    }

    // future identity-links: providerKind = "phone" / "email" / "apple" / "sso"
    // Patterns similar to /identity-links/google/, future spec'и добавляют их.
  }
}
```

**Race resolution**: `GoogleSignInAuthAdapter.lookupOrCreateIdentityLink()` использует Firestore transaction:
```
1. transaction.get(/identity-links/google/{sub})
2. if exists → return existing stableId, abort transaction
3. else → generate UUID v4 → transaction.set(/identity-links/google/{sub}, ...) AND transaction.set(/users/{UUID}, emptyDoc)
4. commit. If fails (concurrent write won) → retry from step 1.
```

This guarantees atomic check-and-create. Security Rules **also** enforce uniqueness (defense-in-depth).

**Test plan**: Firestore Emulator-based instrumentation test simulates concurrent sign-ins, asserts only one UUID created.

---

## R10. Backup exclusion strategy

**Контекст**: HIGH-4 security + core-quality CHK024 — session blob (refreshToken внутри) MUST NOT be auto-backed up к Google Drive.

**`data_extraction_rules.xml`** plan:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="auth_session_v1.preferences.xml"/>
        <exclude domain="sharedpref" path="auth_session_v1.preferences.xml.bak"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="auth_session_v1.preferences.xml"/>
        <exclude domain="sharedpref" path="auth_session_v1.preferences.xml.bak"/>
    </device-transfer>
</data-extraction-rules>
```

`AndroidManifest.xml`:
```xml
<application
    android:dataExtractionRules="@xml/data_extraction_rules"
    ...>
```

**Why exclude device-transfer too**: device-to-device transfer (Google's onboarding service) копирует пользовательские данные на новый телефон. Auth session **должна** требовать explicit re-sign-in на новом устройстве (security best practice). См. Sign-In trap scenario (Edge case в spec.md).

**Verification**: pre-release manual test: factory-reset тестовый device, sign-in, install backup-and-restore via `adb shell bmgr restore`, verify session blob НЕ восстановлен (user должен sign-in заново).

---

## Summary table

| Research item | Decision | Exit ramp cost |
|---------------|----------|----------------|
| R1. `sub` claim extraction | Manual JWT payload decode | 30 min if switch to `GoogleIdTokenVerifier` |
| R2. In-flight signIn scope | Adapter-scoped coroutine + reactive flow | 1-2 days if `SignInCoordinator` extraction needed |
| R3. Firebase Emulator | Emulator Suite + mockk hybrid | 1 day to switch к dedicated qa project |
| R4. Identity-links migration | Inline lookup function; future TODO for own-server | 1 week dev work при cutover |
| R5. Wire format | JSON via kotlinx.serialization | 1 day to migrate к CBOR via schemaVersion 2 |
| R6. minSdk floor | API 24 confirmed | N/A — locked via version catalog |
| R7. SignInTrigger states | 5 explicit states + `rememberSaveable` for inFlight | N/A — design decision finalized |
| R8. Logging policy | Category enum + AuthLog wrapper + Detekt rule | N/A — enforcement automated |
| R9. Firestore Security Rules | Atomic create-only + Firestore transaction | N/A — rules finalized |
| R10. Backup exclusion | Both cloud-backup + device-transfer exclude | N/A |

---

## TL;DR для не-разработчика

Research-фаза разобрала 10 архитектурных вопросов, на которые нужно было дать конкретный ответ до начала кода. По каждому — выбран один путь + есть «запасной выход» (как изменить решение если что-то пойдёт не так).

**Главные решения**:
1. **Идентификатор Google пользователя** (`sub`) извлекается напрямую из ID-токена без дополнительных библиотек (~15 строк кода).
2. **Поворот экрана во время входа** решён через корутину на уровне adapter'а — даже если экран повернулся, вход завершится, новый UI просто увидит обновление через flow.
3. **Тесты** будут гонять Firebase эмулятор (бесплатно, локально, без секретов в CI) для интеграции + mockk для быстрых unit-тестов.
4. **Миграция identity-links на собственный сервер** возможна без переписки кода — функция lookup останется той же, изменится только содержимое.
5. **Формат хранения сессии** — JSON (как в F-CRYPTO), потому что debug-friendly и размер не критичен.
6. **Версия Android** — минимум API 24 (Android 7.0). Все современные устройства поддерживают.
7. **Кнопка входа** имеет 5 чётко определённых состояний (не вошли / загрузка входа / вошли / загрузка выхода / ошибка).
8. **Логирование** — только категории событий, никаких email / токенов / имён в логах (автоматическая проверка Detekt).
9. **Правила Firestore** — запись в таблицу identity-links только один раз для каждого аккаунта (защита от подмены).
10. **Файл сессии исключён** из автобэкапа Google Drive (иначе токен попал бы в облако).

Все 10 решений зафиксированы и готовы к началу кода.
