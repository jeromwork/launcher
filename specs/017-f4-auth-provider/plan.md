# Implementation Plan: F-4 — AuthProvider port + Google Sign-In adapter

**Branch**: `017-f4-auth-provider`
**Date**: 2026-06-18
**Spec**: [specs/017-f4-auth-provider/spec.md](spec.md)
**Status**: Draft

## Summary

F-4 строит **provider-agnostic identity foundation** для всех будущих cloud features Phase 2. Domain port `AuthProvider` в `core/domain/auth/` не знает о Google / Firebase; единственная MVP-реализация — `GoogleSignInAuthAdapter` в `app/androidMain/` (Credential Manager → Firebase Auth → identity-links Firestore lookup → наш собственный UUID). F-5 ConfigCipher, S-2 pairing, S-4 SOS, S-5 photos, S-8 sync, S-9 health monitoring, P-6 recovery — все consumer'ы подписываются на `AuthProvider.currentUser: Flow<AuthIdentity?>` без знания vendor SDK. Это реализация [CLAUDE.md rule 2](../../CLAUDE.md) (ACL) применительно к auth.

**Technical approach** (high-level): три модуля кода. `core/domain/auth/` — pure Kotlin ports, value classes, sealed types. `app/androidMain/auth/` — `GoogleSignInAuthAdapter` (Credential Manager API + Firebase Auth + Firestore identity-links lookup), `EncryptedLocalSessionStore` (EncryptedSharedPreferences для refreshToken + session blob), `AuthAdapterSelector` (runtime GMS detection), composable `SignInTrigger` (UI для входа/выхода, reusable). `core/commonTest/auth/` — `FakeAuthAdapter` + `FakeSessionStore` для unit-тестов и provider-swap fitness function. Cold-start path не блокируется F-4 (FR-035): UI рисуется из локального config cache, F-4 эмитит identity асинхронно через `currentUser` flow.

## Technical Context

- **Language/Version**: Kotlin 2.1+, Kotlin Multiplatform plugin (только `commonMain` + `androidMain` source sets для F-4; iOS — deferred, не in scope).
- **Primary Dependencies**:
  - `androidx.credentials:credentials:<latest>` (Credential Manager API — рекомендованный путь, не deprecated Google Sign-In SDK; per FR-014).
  - `androidx.credentials:credentials-play-services-auth:<latest>` (Google provider для Credential Manager).
  - `com.google.android.libraries.identity.googleid:googleid:<latest>` (для extracting Google ID Token).
  - `com.google.firebase:firebase-auth-ktx:<latest>` (token exchange — Step 2 sign-in; per FR-015).
  - `com.google.firebase:firebase-firestore-ktx:<latest>` (identity-links collection lookup; per FR-016a).
  - `androidx.security:security-crypto:<latest>` (EncryptedSharedPreferences для refreshToken; per FR-020).
  - `kotlinx.serialization.json` (SessionRecord blob serialization; per FR-021).
  - `kotlinx.coroutines.core` (Flow, suspend functions в портах).
  - `kotlinx.datetime` (`Instant` для `SessionRecord.expiresAt`).
  - **No new** dependencies в `core/domain/auth/` — только Kotlin stdlib + coroutines + serialization + datetime.
- **Storage**:
  - **Local** (per device): `EncryptedSharedPreferences` file `auth_session_v1.preferences` в app sandbox — содержит JSON blob `SessionRecord { schemaVersion, stableId, expiresAtEpochMs, refreshToken, extra: Map }`. Excluded from `android:allowBackup` через `data_extraction_rules.xml` (см. HIGH-4 ниже).
  - **Remote** (Firestore):
    - `/identity-links/{providerKind}/{providerAccountId}` → `{ schemaVersion: 1, stableId: UUID, createdAt: Timestamp }` — маппинг провайдер-аккаунт → наш UUID. **Создаётся** при первом sign-in нового аккаунта, **read-only** при последующих sign-in.
    - `/users/{stableId}` → user record (содержание определяется F-5 + consumer-spec'ами; F-4 только создаёт пустой документ при первом sign-in).
- **Testing**:
  - `kotlin-test` (common contract tests для port'а).
  - JVM unit tests с `FakeAuthAdapter` + `FakeSessionStore` — основная масса покрытия.
  - Property tests (Kotest properties) — для dedup на `signIn`, idempotency `signOut`, AuthError exhaustiveness.
  - Compose UI tests для `SignInTrigger` (`StateRestorationTester`).
  - Instrumentation tests на `pixel_5_api_34` для Credential Manager integration (smoke).
  - Firebase Auth Emulator + Firestore Emulator (опционально для CI integration без real Firebase project — см. quickstart.md).
  - **Provider-swap fitness function**: специальный test `ProviderSwapFitnessTest` в `app/src/test/java/` — DI graph A (Google adapter) vs B (Fake adapter) → identical consumer pass rate.
- **Target Platform**:
  - Android **API 24+** (`androidx.credentials` floor).
  - Android **target SDK 35+** (соответствует launcher проект-level convention).
  - Android TV / Google TV — declared в OEM matrix (`tv_4k_api_34`), но не блокирующий MVP (V-4 TV preset spec — post-MVP per `project_mvp_phase_split`).
  - iOS — deferred, не in scope для F-4.
- **Project Type**: KMP library + Android adapter + Compose UI composable (3 source sets в существующих модулях; **новый Gradle модуль `core/auth/` НЕ создаётся** для MVP — package внутри существующего `core/domain/` достаточно per meta-minimization CHK005).
- **Performance Goals**:
  - **Cold-start latency** (FR-035): главный экран рисуется из local config cache за ≤ **500ms perceived latency** на `pixel_5_api_34`. F-4 `SessionStore` read **не** в критическом пути.
  - Sign-in tap → Google bottom-sheet open: ≤ 500ms (Credential Manager handles, мы не optimize).
  - Credential Manager success → `currentUser` emit: ≤ 1s (Firestore identity-links lookup + UUID resolve).
  - Token refresh при `currentSession()` call с истёкшим токеном: ≤ 2s.
  - **Battery**: zero scheduled background work (no WorkManager, no alarms, no foreground services). Refresh on-demand only.
- **Constraints**:
  - **Offline**: local mode forever без internet; sign-in возвращает `AuthError.NetworkError`.
  - **No telemetry / analytics** в F-4 layers (per CLAUDE.md privacy principles).
  - **No PII в logs / crash reports**: structured log categories (`sign_in.failure.{reason}`), не raw email / displayName / token (HIGH-3 logging policy).
  - **Cross-device identity stability**: same Google account на другом устройстве → same UUID (через `/identity-links/google/{sub}` lookup, FR-016a).
- **Scale/Scope**:
  - ~12-15 Kotlin source files в `core/domain/auth/`.
  - ~6-8 Kotlin source files в `app/androidMain/auth/`.
  - ~4-5 Compose files в `app/androidMain/auth/ui/`.
  - ~10-12 test files (commonTest + JVM unit + instrumentation).
  - 2 wire formats: SessionRecord blob (local), identity-link document (Firestore).
  - 5 Detekt rules для enforcement (no vendor imports в domain; no Fake в release; no `signInAnonymously`; no scope creep вне `openid email profile`; no PII в Log calls).
  - 1 Firestore Security Rules файл update (identity-links + users collections).
  - **Effort**: Medium ~2 weeks (per spec SC-012; не включает rewrite consumer-спек 007-012 — это входит в их собственные спеки).

## Constitution Check

*GATE: должен PASS перед research / data-model. Re-check после plan finalization.*

Выполнено через `procedure-constitution-check`. См. отдельную секцию [Constitution Check Report](#constitution-check-report) ниже.

## Project Structure

### Documentation (this feature)

```text
specs/017-f4-auth-provider/
├── plan.md              # This file
├── research.md          # ID Token sub claim extraction, Firebase Auth Emulator, in-flight signIn scope, identity-links migration
├── data-model.md        # AuthIdentity, User, SessionRecord (internal), AuthError, SubscriptionState, IdentityLink Firestore doc
├── quickstart.md        # Build/test commands, Firebase setup, Emulator config, SHA-1 fingerprints, OAuth Consent
├── contracts/
│   ├── session-record-v1.md       # Local wire format (EncryptedSharedPreferences blob)
│   ├── identity-link-v1.md        # Firestore document `/identity-links/{provider}/{accountId}`
│   ├── auth-provider-port.md      # Domain port contract (signIn/signOut/currentUser semantics + invariants)
│   └── firestore-security-rules.md # Security Rules для identity-links + users collections
├── spec.md
└── checklists/          # 14 quality checklists + _overview.md (passed clarify + scenarios stages)
```

### Source Code (repository root)

```text
core/
└── domain/                                   # Existing module
    └── src/commonMain/kotlin/family/launcher/domain/
        └── auth/                              # NEW package для F-4
            ├── AuthProvider.kt                 # port interface (signIn/signOut/currentUser)
            ├── AuthIdentity.kt                 # data class { stableId, email?, displayName? } — без providerKind
            ├── AuthError.kt                    # sealed type (NetworkError, Cancelled, NoEmail, ProviderUnavailable, Unknown)
            ├── User.kt                         # data class { id, identityKeys?, email?, displayName?, subscriptionState }
            ├── SubscriptionState.kt            # sealed: Unknown, LocalOnly, Trial, Active, Expired (MVP всегда Unknown)
            ├── Outcome.kt                      # already exists или создаётся, если ещё нет — sealed Result-like type
            └── internal/                       # consumer'ы НЕ импортируют это package
                ├── SessionStore.kt              # port interface (save, current, clear, sessionChanges)
                └── SessionRecord.kt             # internal wire format { schemaVersion, stableId, expiresAt?, refreshToken?, extra }

app/
└── androidMain/kotlin/family/launcher/app/
    └── auth/                                  # NEW package
        ├── GoogleSignInAuthAdapter.kt          # Real implementation (Credential Manager + Firebase + identity-links)
        ├── EncryptedLocalSessionStore.kt       # Real implementation (EncryptedSharedPreferences + JSON)
        ├── AuthAdapterSelector.kt              # Runtime GMS detection → pickAdapter()
        ├── di/
        │   └── AuthModule.kt                   # DI wiring (debug/test → Fake; release → real)
        ├── logging/
        │   └── AuthLog.kt                      # Structured log categories (no PII, no tokens)
        └── ui/
            └── SignInTrigger.kt                # Compose composable (5 states: NotSignedIn / Loading / SignedIn / SigningOut / Error{reason})

core/
└── domain/src/commonTest/kotlin/family/launcher/domain/auth/
    ├── FakeAuthAdapter.kt                     # Pre-seeded users + test triggers (simulateRefreshFailure / NoEmail / Cancellation)
    └── FakeSessionStore.kt                    # In-memory HashMap-based, deterministic

app/
└── androidMain/src/test/kotlin/family/launcher/app/auth/
    ├── AuthProviderContractTest.kt            # Contract: same behaviour для Fake и Google adapter (mocked at boundary)
    ├── SessionRecordRoundtripTest.kt           # Wire-format roundtrip + backward-compat (v1 → v2 stub)
    ├── ProviderSwapFitnessTest.kt              # DI graph A vs B identical consumer pass rate
    ├── EncryptedLocalSessionStorePropertyTest.kt # Idempotency, corrupted blob handling
    └── SignInTriggerStateTest.kt               # Compose StateRestorationTester (rotation, locale change)

app/
└── androidMain/src/androidTest/kotlin/family/launcher/app/auth/
    ├── GoogleSignInAdapterInstrumentationTest.kt    # Smoke с Firebase Auth Emulator
    ├── EncryptedLocalSessionStoreInstrumentationTest.kt # TEE-backed Keystore real test
    ├── LocalModeNoSignInTest.kt                # FR-030 negative integration test
    └── ColdStartLatencyTest.kt                 # FR-035 cold-start UI renders before F-4 init

app/
└── androidMain/src/main/res/xml/
    └── data_extraction_rules.xml               # UPDATE: exclude auth_session_v1.preferences from auto-backup

firestore/                                      # Existing project Firestore Rules dir
└── firestore.rules                              # UPDATE: add identity-links + users collections rules

config/detekt/
└── auth-rules.yml                               # NEW Detekt custom rules
                                                # (no vendor imports в core/domain/auth; no Fake* в :app:release; no signInAnonymously; OAuth scope whitelist; no PII в Log calls)
```

**Structure Decision**: F-4 **не** создаёт новый Gradle subproject. Использует existing `core/domain/` и `app/androidMain/` модули, добавляя packages `auth/`. Это обосновано per **meta-minimization CHK005**: один real consumer adapter (Android), отсутствие cross-platform need в MVP, premature module decomposition нарушила бы CLAUDE.md rule 4 (MVA). Inline TODO в `core/domain/auth/AuthProvider.kt`: `// TODO(extract-as-module): if F-4 ever needs iOS adapter или standalone library — extract to core:auth submodule.`

## Test Strategy

Per CLAUDE.md rule §6 (mock-first) и §7 (fitness functions):

**1. Contract tests (`commonTest`)**: один contract test suite запускается **дважды** — на `FakeAuthAdapter` и на mocked `GoogleSignInAuthAdapter` (через test boundary с подменёнными Credential Manager / Firebase Auth interfaces). Обеспечивает identical behaviour для всех consumer'ов независимо от провайдера. Покрывает:
- `signIn()` happy path → `AuthIdentity` emit в `currentUser`.
- `signOut()` clears SessionStore, emits null.
- `currentUser` flow эмитит null до session restore (cold-start case).
- AuthError exhaustive paths.

**2. Property tests** (Kotest properties в `commonTest`):
- Idempotency: `signOut()` дважды подряд = same state как один раз.
- Dedup: `signIn()` вызвано N раз параллельно → max 1 active Credential Manager bottom-sheet.
- AuthError exhaustive: каждый AuthError variant возвращается хотя бы одним симулированным путём в FakeAuthAdapter.
- SessionRecord roundtrip: serialize → deserialize → equal (FR-022).
- Corrupted blob: random byte flip в serialized blob → graceful `current() == null`, не throw.

**3. JVM unit tests** (`app/androidMain/src/test/`):
- `SessionRecordRoundtripTest`: wire format v1 → read → equal.
- `SessionRecordBackwardCompatTest`: hardcoded v1 fixture file → read → expected fields populated.
- `ProviderSwapFitnessTest`: DI graph A (Google with mocked Firebase) vs B (Fake) → consumer tests pass identically (US 6 / SC-008).
- `SignInTriggerStateTest`: Compose `StateRestorationTester` → rotation / locale change / `fontScale=2.0` все survive.
- `AuthLogPolicyTest`: проверяет что Logger NEVER receives `AuthIdentity`, `SessionRecord`, `refreshToken` direct parameters (HIGH-3).

**4. Instrumentation tests** (`app/androidMain/src/androidTest/`, `pixel_5_api_34`):
- `GoogleSignInAdapterInstrumentationTest`: smoke с Firebase Auth Emulator (или real Firebase project в `qa` flavor).
- `EncryptedLocalSessionStoreInstrumentationTest`: real Android Keystore TEE roundtrip; verify session blob НЕ содержит plaintext refreshToken (scan bytes).
- **`LocalModeNoSignInTest`** (FR-030): cold-start app → wizard local mode → main screen → 10s idle. Verify: `AuthProvider.currentUser.first() == null`, no network traffic к Firebase Auth (через test-side network interceptor).
- **`ColdStartLatencyTest`** (FR-035): cold-start → measure time to first UI paint of main screen. Assert: ≤ 500ms perceived latency. Assert: `SessionStore.current()` ещё не завершился к моменту первого paint (через test hooks).

**5. Detekt fitness functions** (компилируются как unit tests в CI):
- `NoVendorImportsInDomain`: ни один файл в `core/domain/auth/` не имеет import `com.google.*` / `androidx.credentials.*` / `com.firebase.*` / любые vendor SDK (SC-003, SC-010-a).
- `NoVendorImportsInConsumers`: `core/config/` / `core/pairing/` / `app/sos/` / `app/photos/` чисты от auth SDK imports (SC-010-b).
- `NoFakeInRelease`: `FakeAuthAdapter` / `FakeSessionStore` не импортируются в `:app:release` source set (SC-010-c).
- `NoAnonymousAuth`: ни один файл не содержит `signInAnonymously` или `FirebaseAuth.getInstance().signInAnonymously` (FR-029, SC-010-d).
- `OAuthScopeWhitelist`: ни один Credential Manager request не запрашивает scope вне `openid email profile` (HIGH-4 security).
- `NoPIIInAuthLog`: `Log.*` calls в `auth/` package не передают `AuthIdentity` / `SessionRecord` / `String token` напрямую (HIGH-3 logging).

**6. Fake adapter usage**: ВСЕ consumer'ы (F-5 ConfigCipher tests, S-2 PairingFlow tests, S-8 sync tests, etc.) пишут тесты с `FakeAuthAdapter` + `FakeSessionStore`. ConfigCipher тест: pre-seed `FakeAuthAdapter` users → call `ConfigCipher.encrypt()` → key derived from `AuthIdentity.stableId` matches expected. Никакой Firebase / Google в test path.

## Dependency Impact

Новые dependencies (Article XIII justification):

| Dependency | Source | Why needed | Bounded to |
|------------|--------|------------|------------|
| `androidx.credentials:credentials` | AndroidX | Credential Manager API (Google recommended, per FR-014, decision 2026-05-30/08) | `app/androidMain/auth/GoogleSignInAuthAdapter` |
| `androidx.credentials:credentials-play-services-auth` | AndroidX | Google provider plugin для Credential Manager | Same |
| `com.google.android.libraries.identity.googleid` | Google | Extract `sub` claim из Google ID Token (для identity-links lookup, FR-016a) | Same |
| `com.google.firebase:firebase-auth-ktx` | Firebase | Step 2 sign-in (ID Token → Firebase JWT exchange, FR-015) | Same |
| `com.google.firebase:firebase-firestore-ktx` | Firebase | identity-links + users collections (FR-016a) — **NB**: Firestore SDK уже в проекте через S-7 / S-8 будущие — F-4 не вводит новую SDK depend, но добавляет new collection use | Same |
| `androidx.security:security-crypto` | AndroidX | EncryptedSharedPreferences с TEE-backed AES master key (FR-020) | `app/androidMain/auth/EncryptedLocalSessionStore` |

**Не добавляются** в `core/domain/auth/`: ни одного из выше. Domain depends только на existing `kotlinx-coroutines`, `kotlinx-serialization-json`, `kotlinx-datetime`, Kotlin stdlib.

**Article XIII compliance**: каждая dependency имеет single-purpose adapter usage; vendor-agnostic domain не aware о существовании этих SDK; provider-swap fitness function (SC-008) проверяет что замена adapter'а не требует изменений в consumer'ах.

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **In-flight signIn coroutine cancelled at rotation** (state-management open item HIGH-5) | Medium | High UX impact (silent fail) | Hoist signIn coroutine к adapter scope (application-level singleton). Result delivered через `currentUser` flow, не direct callback. См. research.md §"In-flight signIn scope". |
| **Identity-links race condition** (двое устройств одного аккаунта signIn одновременно → создают 2 UUID) | Low | High data corruption | Firestore transaction в `GoogleSignInAuthAdapter.firstSignIn(sub)`: check-and-create атомарно. Security Rules: write only if document does NOT exist (no overwrites). См. contracts/firestore-security-rules.md. |
| **`androidx.credentials` API changes** между library versions | Medium | Medium maintenance cost | Wrap Credential Manager calls в private functions внутри adapter; lock library version в `version-catalog.toml`. Detekt rule запрещает Credential Manager imports вне adapter. |
| **Refresh token theft с rooted device** | Low | High account compromise risk | EncryptedSharedPreferences с TEE-backed AES master key (L0 уровень per decision 03). Inline TODO для миграции на `SecureKeyStore` из F-CRYPTO (wrap pattern сильнее) когда F-5 готов. Firebase Auth server-side device fingerprint / IP проверки — additional defense. |
| **Cold-start latency violation** (UI блокируется F-4 init) | Medium | High UX impact (slow feel) | FR-035 explicit + `ColdStartLatencyTest` в CI. `AuthProvider` инициализация в Application.onCreate() — **только** registration в DI, без чтения SessionStore. SessionStore read в background coroutine после first paint. |
| **OAuth scope creep** (future maintainer добавляет calendar / contacts scope) | Low | High Privacy Policy compliance failure | Detekt rule `OAuthScopeWhitelist` на CredentialManager request строки. Pre-release task: Google OAuth verification review. |
| **PII leak в crash reports / logs** | Medium | High GDPR / Play Policy risk | Detekt rule `NoPIIInAuthLog`. `AuthLog.kt` exposes только category-based methods (`logSignInAttempt(providerKind)`, не `log(authIdentity)`). |
| **Backup of refreshToken на Google Drive** (auto-backup user-controlled) | Medium | High account hijack risk if backup compromised | `data_extraction_rules.xml`: explicit `<exclude>` для `auth_session_v1.preferences` (HIGH-4 security). |
| **Firebase Auth project mis-configuration** (SHA-1 fingerprints, OAuth Consent) → pre-release task miss | Medium | High release blocker | Pre-release checklist + admin runbook (см. quickstart.md). |
| **Country-ban Google в jurisdictions** (Россия в гипотетическом сценарии) | Low (medium-term) | High user lockout если идентификатор привязан к Google | UUID stableId через identity-links (FR-016a clarification Q1). PhoneAuthAdapter может быть добавлен additive без миграции UID. Country-ban exit ramp inline TODO в `GoogleSignInAuthAdapter`. |

## Required Context Review

Per Article XII §7, каждый relevant документ:

- **CLAUDE.md** — rules 1, 2, 4, 5, 6, 8 (центральные для F-4).
- **`.specify/memory/constitution.md`** — Article XVI gates checked.
- **Decisions**:
  - [`docs/product/decisions/2026-05-30-f4-identity/`](../../docs/product/decisions/2026-05-30-f4-identity/) — 9 файлов identity модели.
  - [`docs/product/decisions/2026-06-15-deferred-cloud/01-deferred-sign-in.md`](../../docs/product/decisions/2026-06-15-deferred-cloud/01-deferred-sign-in.md) — activation timing.
  - [`docs/product/decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md`](../../docs/product/decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md) — subscription_state model.
- **Use cases**: [`docs/product/use-cases/05-pairing-identity-trust.md`](../../docs/product/use-cases/05-pairing-identity-trust.md) §Identity (D-Pair-1).
- **Server-roadmap**: [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) — добавить SRV-AUTH-IDENTITY-001 (identity-links migrates first).
- **Project backlog**: [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) AUTH-001.
- **Memory** (auto-memory):
  - [`project_auth_provider_architecture`](../../C:/Users/user/.claude/projects/c--work-launcher/memory/project_auth_provider_architecture.md) — F-4 mega-block context.
  - [`project_deferred_cloud_architecture`](../../C:/Users/user/.claude/projects/c--work-launcher/memory/project_deferred_cloud_architecture.md) — переопределяет activation timing.
  - [`project_decisions_2026_05_30`](../../C:/Users/user/.claude/projects/c--work-launcher/memory/project_decisions_2026_05_30.md).
  - [`project_mvp_phase_split`](../../C:/Users/user/.claude/projects/c--work-launcher/memory/project_mvp_phase_split.md).
  - [`project_config_cache_model`](../../C:/Users/user/.claude/projects/c--work-launcher/memory/project_config_cache_model.md) — three-tier config model, S-8 territory cross-ref.
- **F-CRYPTO** [spec 016](../016-f-crypto-core-module/spec.md): F-4 не depends runtime; inline TODO для будущей миграции `EncryptedLocalSessionStore` → `SecureKeyStore`.
- **F-3 wizard** spec 015 ([specs/015-wizard-localization-senior-ui/spec.md](../015-wizard-localization-senior-ui/spec.md)): screen 2 двухкнопочный UI — F-3 territory, использует `SignInTrigger` из F-4.

## Constitution Check Report

**Gates** (Article XVI):

| Gate | Status | Notes |
|------|--------|-------|
| **1. Architecture (domain isolation)** | ✅ PASS | `core/domain/auth/` — pure Kotlin, no vendor imports. Detekt rule + Gradle dependency check enforces. Provider-swap fitness function (SC-008) проверяет automatedly. См. checklist-domain-isolation 16/16. |
| **2. Core/System Integration** | ✅ PASS | F-4 не интегрирует с launcher core напрямую. Wizard (F-3) imports `SignInTrigger`. Consumer-spec'и subscribe на `currentUser` flow. No tight coupling. |
| **3. Configuration** | ✅ PASS | Wire formats имеют `schemaVersion: 1` (SessionRecord и identity-link Firestore doc). Backward-compat read test обязателен. `OAuth scopes` — hardcoded constants в adapter, не runtime config. |
| **4. Required Context Review** | ✅ PASS | См. секцию выше. Все relevant decisions / memory / specs linked. |
| **5. Accessibility (senior-safe)** | ⚠️ DEFERRED | `SignInTrigger` визуальный baseline (≥18sp / ≥56dp / ≥16dp / 4.5:1 contrast) — explicit в FR-033 + plan; Google Credential Manager UI — не кастомизируется (decision 2026-05-30/08), accepted per Article VIII §7 documented constraint. Open item MEDIUM-9 для plan-stage UI test. |
| **6. Battery / Performance** | ✅ PASS | Zero background work. Refresh on-demand. `ColdStartLatencyTest` (FR-035) — CI gate. См. perf goals выше. |
| **7. Testing** | ✅ PASS | Mock-first (FakeAuthAdapter), contract tests, property tests, fitness functions (provider-swap, Detekt rules), instrumentation. См. Test Strategy section. |
| **8. Simplicity (MVA)** | ✅ PASS | `ProviderKind` enum **удалён** в clarify (Q4) — exemplary MVA. No new Gradle module (CHK005). No premature abstractions (CloudFeatureGate отброшен в clarify Q5). `KeyRotation` / `KeyEscrow` interface-only stubs — pattern из F-CRYPTO применён здесь к `AuthorizedRequestSigner` (inline TODO). |

**Overall**: **8/8 PASS** (1 gate accepted as documented constraint per Article VIII §7).

## Rollout / Verification

**Phase 1 — code merge** (F-4 spec ready):
1. Detekt rules added и **должны** проходить green в CI.
2. JVM unit tests (FakeAdapter + roundtrip + ProviderSwap fitness) **должны** проходить.
3. Instrumentation tests (`LocalModeNoSignInTest`, `ColdStartLatencyTest`) **должны** проходить на `pixel_5_api_34`.
4. `GoogleSignInAdapterInstrumentationTest` — passing с Firebase Auth Emulator (local CI).
5. Manual smoke на real device + real Google account — pre-merge approval (1 walk-through).

**Phase 2 — pre-release blockers** (перед Phase 2 cloud features ship):
1. Firebase Auth project configured (admin task): Google provider enabled, SHA-1 fingerprints (debug + release) added.
2. OAuth Consent Screen в Google Cloud Console: scopes `openid email profile` declared, app verified Google review (1-2 weeks). _Documented в quickstart.md._
3. Firestore Security Rules deployed (identity-links + users).
4. Privacy Policy updated с email/displayName/profile photo URL collection.
5. Play Console Data Safety form prepared с конкретными entries (см. core-quality checklist CHK012).
6. `data_extraction_rules.xml` updated (exclude auth session blob).

**Phase 3 — post-release monitoring** (after F-4 ships):
- Crashlytics dashboard: `auth.error.*` categories tracked. Anomaly detection если sign-in failure rate > 5%.
- ANR monitoring: F-4 не должен contribute к ANRs (zero blocking ops). Trigger investigation если any ANR tagged `Auth`.
- Identity-links collection size monitoring (one entry per unique account ever signed in across all devices).
- Token refresh failure rate: если > 1% — investigate Firebase Auth API health / our adapter logic.

**Fitness functions in CI** (per Article XVI § Article VII):
- All 6 Detekt rules listed в Test Strategy MUST pass green.
- ProviderSwapFitnessTest MUST pass green (rule 2 ACL provable).
- ColdStartLatencyTest MUST assert ≤ 500ms (FR-035 enforced).
- LocalModeNoSignInTest MUST assert zero Firebase Auth network calls (FR-030 enforced).

---

## TL;DR для не-разработчика

**Что строим в коде**:
- В чистой части приложения (`core/domain/auth/`) — описание интерфейса «вход в аккаунт» без слов Google или Firebase. Это абстрактная дверь.
- В Android-части (`app/androidMain/auth/`) — реальная реализация этой двери через Google (Credential Manager + Firebase). Это **один файл**, который будет переписан если завтра уберём Google.
- Маленький UI-блок (`SignInTrigger`) с кнопкой «Войти в аккаунт» / «Выйти», который можно встроить куда угодно (сейчас — в визард).
- Заглушки для тестов (`FakeAuthAdapter`) — позволяют тестировать всё без реального Google.

**Ключевые архитектурные решения**:
1. **Никакого нового Gradle модуля** — добавляем папки внутри существующих. Меньше сложности.
2. **UUID-идентификатор** хранится в отдельной Firestore таблице `/identity-links/...`. При переезде на свой сервер эта таблица — **первая в очереди миграции**, UUID не меняются.
3. **Главный экран рисуется ДО входа в аккаунт** — приложение не ждёт пока F-4 проверит сессию (FR-035 cold-start invariant). Это критически важно для скорости.
4. **Никакой работы в фоне** — F-4 ничего не делает пока пользователь не нажмёт кнопку. Батарею не сажает.
5. **6 автоматических проверок** (Detekt rules) гарантируют что Firebase / Google не «утекут» в другие части кода.

**Что нужно сделать перед релизом** (pre-release задачи, не блокируют merge кода):
1. Настроить Firebase проект (admin задача, разовая).
2. Получить от Google OAuth Consent Screen review (1-2 недели).
3. Обновить Privacy Policy и Play Console Data Safety форму.
4. Добавить исключение из автобэкапа Google Drive для файла сессии.

**Риски** — 10 штук, все имеют конкретные митigации. Главный — «вход в Google прерван поворотом экрана» — решается через специальный coroutine scope на уровне адаптера.

**Готовность к `/speckit.tasks`**: после прохождения checklists этого plan stage.
