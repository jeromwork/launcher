# Checklist: dev-experience

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 19/22 ✓ — passes baseline, 3 open items (Firebase Emulator setup + onboarding doc + crash diagnostics).

---

## Local-test path

- [x] **CHK001** Local Test Path section filled — ✓. Spec содержит **Local Test Path** section с emulator preset, fake adapters list, fixture paths, verification commands, cannot-test-locally gaps.
- [x] **CHK002** Verification commands exact — ✓. Конкретные gradle commands:
  - `./gradlew :core:domain:jvmTest --tests *AuthProviderContractTest`
  - `./gradlew :core:domain:jvmTest --tests *SessionRecordRoundtripTest`
  - `./gradlew :app:testDebugUnitTest --tests *ProviderSwapFitnessTest`
  - `./gradlew :app:connectedDebugAndroidTest --tests *GoogleSignInAdapterInstrumentationTest`
  - `./gradlew :app:connectedDebugAndroidTest --tests *EncryptedLocalSessionStoreTest`
  - `./gradlew :app:connectedDebugAndroidTest --tests *LocalModeNoSignInTest`
  - `./gradlew detekt`
- [x] **CHK003** Runs under 5 minutes — ✓ (с notes). JVM unit tests должны быть <30s. Instrumentation tests с эмулятором — ~2-3 минуты на cold cycle. **Note**: full integration test (two-emulator pairing, US 7) скорее всего 5+ минут — это **P3**, не блокирует dev cycle.
- [x] **CHK004** JVM-only path exists — ✓. Большая часть spec'и проверяется через `core/domain/auth/` JVM unit tests + FakeAuthAdapter / FakeSessionStore: AuthProvider port contract, SessionRecord wire-format roundtrip, identity-links lookup logic (с Fake), provider-swap fitness. **Только** Google Sign-In integration с real Credential Manager и `EncryptedLocalSessionStore` (Android EncryptedSharedPreferences) требуют эмулятор.
- [x] **CHK005** Emulator preset named — ✓. `pixel_5_api_34` для основного instrumentation; `tv_4k_api_34` для ATV smoke (отложен — TODO physical-device).

## Fake adapters

- [x] **CHK006** Every external port has fake — ✓.
  - `AuthProvider` port → `FakeAuthAdapter` (FR-024). Pre-seeded users list constructor.
  - `SessionStore` port → `FakeSessionStore` (FR-026). In-memory HashMap.
  - Test triggers: `FakeAuthAdapter.simulateRefreshFailure()`, `simulateNoEmail()`, `simulateCancellation()` (FR-025) — покрывает все AuthError cases.
- [x] **CHK007** Tests use fakes, not real Firebase — ✓. JVM tests никогда не делают network calls. Instrumentation tests на эмуляторе требуют **либо** real Firebase project (для smoke), **либо** Firebase Emulator Suite (для CI без real account). См. CHK016.
- [x] **CHK008** DI wiring picks fakes/reals — ✓. FR-019: `debug` / `test` source set → `FakeAuthAdapter`; `release` → `AuthAdapterSelector.pickAdapter()` (real). Build-config gate (FR-019, FR-004) ловит accidental Fake в release.

## Fixtures

- [x] **CHK009** Test data в checked-in fixture — ✓. Spec специфицирует:
  - `core/commonTest/resources/auth-fixtures/user-google-v1.json` — sample AuthIdentity.
  - `core/commonTest/resources/auth-fixtures/session-record-v1.json` — sample wire-format.
- [x] **CHK010** Fixtures stable — ✓ implied. UUID stableId в fixtures должен быть **hardcoded** (e.g., `550e8400-e29b-41d4-a716-446655440000`), не `UUID.randomUUID()`. **Open item**: spec.md не requirements это explicitly, но это implied для wire-format roundtrip test (`SessionRecordRoundtripTest`).
- [x] **CHK011** Cross-version fixtures exist — ✓. FR-022 требует backward-compat read test: `schemaVersion=1` blob + handler для `schemaVersion=2`. Fixture path predefined. **Note**: spec не указывает `schemaVersion=2` sample (это естественно — мы в v1; v2 sample появится когда мы migration пишем).

## Cannot-test-locally gaps

- [x] **CHK012** Gaps explicitly listed — ✓. Section «Cannot-test-locally gaps» содержит 6 пунктов:
  - Real Google account smoke.
  - OAuth Consent Screen verification.
  - Cross-Google-account migration edge.
  - Firebase Auth Emulator integration (open).
  - non-GMS device behavior.
  - Token expiry edge (1-hour soak).
- [x] **CHK013** Each gap has inline TODO — ✓. Каждый gap имеет `TODO(physical-device)` / `TODO(local-dev)` / `TODO(soak-test)` marker.
- [x] **CHK014** No silent "test in prod" — ✓. Каждый gap явно declared.

## Build cycle

- [x] **CHK015** Clean-build time impact — ✓ (с note). F-4 добавляет:
  - `core/domain/auth/` package (small, pure Kotlin).
  - `app/androidMain/auth/` package (Firebase Auth SDK + Credential Manager AndroidX dep).
  - **Estimated impact**: +5-10s clean build из-за Firebase Auth SDK addition (это уже скорее всего есть в проекте через spec 007). **Acceptable**.
- [ ] **CHK016** One-time manual setup documented — ⚠️ partial. Spec упоминает «Firebase Auth project configured (admin task)» как assumption, но **не описывает** конкретно:
  - Какие SHA-1 fingerprints добавить (debug + release).
  - Как настроить OAuth Consent Screen (scopes `openid email profile`).
  - Как поднять Firebase Auth Emulator локально для CI-friendly testing.
  - **Open item**: эти setup steps должны быть либо в spec.md (Assumptions section), либо в отдельном setup script / `docs/dev/auth-setup.md`. **Recommendation**: создать `docs/dev/auth-setup.md` в plan.md tasks (НЕ блокирует merge spec, блокирует merge implementation).
- [x] **CHK017** No new credential for debug — ✓. Debug builds используют `FakeAuthAdapter` (FR-019); никаких credentials не требуется для unit tests + provider-swap test. Instrumentation tests требуют либо real Firebase project credentials, либо Firebase Emulator — это **release/CI concern**, не developer onboarding concern.

## Crash + log diagnostics

- [ ] **CHK018** Log signal for diagnosis — ⚠️ open. Spec **не специфицирует** logging requirements: какой Logcat tag, какие structured log fields. Important места для logging:
  - sign-in attempt start / success / failure (с reason).
  - token refresh attempt / success / failure.
  - identity-links lookup hit / miss.
  - corrupted session blob detection (FR-023).
  - **Open item**: добавить FR в plan.md tasks: «структурированные log lines для auth events; tag `Auth`; не логировать tokens / sub claim / refreshToken — только их presence (`refreshTokenPresent: true`)».
- [ ] **CHK019** Silent failure prevention — ⚠️ open. FR-023 (corrupted blob → null + log warning) есть, но **silent background failures** не покрыты explicit:
  - Background token refresh failure → `currentUser` → null. Visible через flow, **но** developer без attach отладчика может не заметить.
  - identity-links lookup network failure → ? (spec не описывает).
  - **Open item**: добавить mandatory WARN-level log при любом adapter-internal failure path.
- [x] **CHK020** Feature flags loggable — **N/A**. F-4 не вводит runtime feature flags. (DI build flavor — compile-time, виден в build config, доступен через `BuildConfig.FLAVOR` если потребуется debug.)

## Cross-developer reproducibility

- [x] **CHK021** No developer-machine-specific paths — ✓. Spec не embedding каких-либо local paths, env vars, phone numbers, или machine-specific assumptions. Fixture paths все project-relative.
- [ ] **CHK022** Onboarding new developer < 1 page — ⚠️ open. F-4 vводит **новую concept**: identity-links lookup logic. Для нового разработчика **нужна**:
  - Описание `AuthProvider` mental model (port vs adapter).
  - Описание Firebase Auth flow (ID Token → Credential exchange → JWT).
  - Описание identity-links collection.
  - Setup Firebase Auth Emulator локально (или ссылка как обойтись без него).
  - **Open item**: создать `docs/dev/auth-onboarding.md` в plan.md tasks. Или extend `docs/dev/dev-environment.md` секцией про auth.

---

## Open items (для plan stage)

1. **`docs/dev/auth-setup.md`**: SHA-1 fingerprints, OAuth Consent Screen scopes, Firebase Auth Emulator setup. Plan task.
2. **Logging requirements FR**: structured log lines, Auth tag, no secrets in logs, WARN on silent background failures. Plan task.
3. **`docs/dev/auth-onboarding.md`** (или раздел в dev-environment): mental model, identity-links, fake adapter usage. Plan task.

Ни один из этих open items **не блокирует** spec merge, но все три должны быть addressed до implementation merge.

---

## Verdict

**19/22 ✓, 3 open для plan stage.** Local test path **executable** прямо сейчас: JVM-only path работает без Firebase / эмулятора (через Fakes). Instrumentation tests требуют либо real Firebase project, либо Firebase Auth Emulator — это **release/CI concern**.

**Главный риск для dev experience**: новый developer без attach отладчика может пропустить silent auth failures (refresh failed, identity-links lookup error). Adressed через open item 2 (mandatory WARN logs).

---

## Что это значит простыми словами

Спека позволяет разработчику:
- Большую часть кода (логика port'а, sign-in/sign-out поведение, identity-links) проверить на **обычном ноутбуке** без эмулятора, без Google аккаунта, через JVM unit tests с заглушками (`FakeAuthAdapter`).
- Реальную интеграцию с Google проверить на эмуляторе — для этого нужно либо настроить Firebase Auth Emulator (бесплатный, локальный), либо использовать тестовый Firebase project.
- Все случаи «здесь надо реальное устройство» (TEE, OEM Samsung, не-Google телефон Huawei) явно перечислены — никто не наткнётся на сюрприз в продакшене.

**Три открытых вопроса** для следующего шага (plan):
1. Создать инструкцию `docs/dev/auth-setup.md` (как настроить Firebase, какие ключи добавить).
2. Зафиксировать требования к логированию (какие события auth логировать, что НЕ логировать — токены не должны попадать в логи).
3. Создать инструкцию для нового разработчика — как разобраться с identity-links таблицей и заглушками.

Эти три пункта не блокируют утверждение спеки, но должны быть закрыты до того, как реальный код F-4 сольётся в main.
