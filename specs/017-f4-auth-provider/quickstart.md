# Quickstart: F-4 — AuthProvider + Google Sign-In

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-06-18

Dev setup guide для разработчика, который впервые работает с F-4.

---

## Prerequisites

- JDK 21 (per project convention — F-CRYPTO setup notes).
- Android Studio Hedgehog (2023.1.1)+ или command-line Gradle 8.5+.
- Android SDK Platform 35 + Build Tools 35.
- Emulator AVD: `pixel_5_api_34` (see project `android-emulator` skill).
- Optional: Firebase CLI (для emulator suite testing).

---

## Build

```powershell
# Domain unit tests (no Android, fastest feedback)
./gradlew :core:domain:jvmTest --tests "family.launcher.domain.auth.*"

# App unit tests (JVM, FakeAuthAdapter-based)
./gradlew :app:testDebugUnitTest --tests "family.launcher.app.auth.*"

# Detekt rules (fitness functions for ACL enforcement)
./gradlew detekt

# Compose UI tests (StateRestorationTester для SignInTrigger)
./gradlew :app:testDebugUnitTest --tests "family.launcher.app.auth.ui.SignInTriggerStateTest"

# Instrumentation tests (требует эмулятор)
./gradlew :app:connectedDebugAndroidTest --tests "family.launcher.app.auth.*"
```

---

## Firebase Setup (one-time per developer)

F-4 требует Firebase Auth project для real-Google-account integration tests. Для CI и local development рекомендуется **Firebase Emulator Suite** (бесплатно, local).

### Option A: Firebase Emulator Suite (recommended для dev)

```powershell
# Install Firebase CLI (если ещё нет)
npm install -g firebase-tools

# Login (one-time)
firebase login

# Initialize emulators (run в repo root)
firebase init emulators
# - выбрать: Authentication Emulator + Firestore Emulator
# - default ports: Auth 9099, Firestore 8080

# Start emulators (отдельный terminal)
firebase emulators:start

# В тестах: FirebaseEmulatorRule подключит SDK к localhost
```

`firebase.json` (committed в repo):
```json
{
  "emulators": {
    "auth": { "port": 9099 },
    "firestore": { "port": 8080 },
    "ui": { "enabled": true, "port": 4000 }
  }
}
```

### Option B: Real Firebase project (только для pre-release smoke)

1. Создать Firebase project в [Firebase Console](https://console.firebase.google.com/).
2. Add Android app:
   - Package: `family.launcher.app` (или whatever project actually uses).
   - SHA-1 fingerprint debug:
     ```powershell
     ./gradlew signingReport
     # копировать SHA-1 из `Variant: debug`
     ```
   - SHA-1 fingerprint release: same command, `Variant: release`. **NB**: release fingerprint требует release keystore — может быть admin-managed.
3. Enable Google sign-in provider в Firebase Console → Authentication → Sign-in method.
4. Скачать `google-services.json`, положить в `app/google-services.json`. **Не commit в repo** (`.gitignore`).
5. OAuth Consent Screen в [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → OAuth consent screen:
   - User type: External.
   - App name, support email, developer email.
   - Scopes: **`openid`, `email`, `profile`** (только эти).
   - Test users (для unverified app): add Google accounts тех кто будет тестировать.
   - **NB**: для production launch — Google verification (1-2 weeks process).

---

## Run signed-in flow (manual smoke)

1. Start Firebase Emulator (`firebase emulators:start`).
2. Build debug app: `./gradlew :app:installDebug`.
3. Start emulator: `pixel_5_api_34`.
4. Launch app:
   - Wizard screen 1 (welcome) → screen 2 «Настройка приложения».
   - Tap **«Войти в аккаунт»**.
   - В Emulator: Credential Manager откроет picker → выбрать pre-seeded test user (`test-admin@example.com`).
   - Confirm sign-in.
   - Wizard screen 2 закроется → main screen рисуется с config (если есть).

### Pre-seeded test users (Emulator)

```powershell
# Create test users в Firebase Auth Emulator via REST API
curl -X POST 'http://localhost:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake' `
  -H 'Content-Type: application/json' `
  -d '{ "email": "test-admin@example.com", "password": "test-password", "returnSecureToken": true }'

curl -X POST 'http://localhost:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake' `
  -H 'Content-Type: application/json' `
  -d '{ "email": "test-senior@example.com", "password": "test-password", "returnSecureToken": true }'
```

---

## Provider-swap test (fitness function для CLAUDE.md rule 2 ACL)

Special test verifies что заменяя `GoogleSignInAuthAdapter` на `FakeAuthAdapter` в DI, все consumer code продолжает работать.

```powershell
./gradlew :app:testDebugUnitTest --tests "*ProviderSwapFitnessTest"
```

Если test fails — значит rule 2 ACL нарушен где-то (consumer импортирует vendor SDK).

---

## Cold-start latency test (FR-035)

```powershell
./gradlew :app:connectedDebugAndroidTest --tests "*ColdStartLatencyTest"
```

Test:
- Force-stop app.
- Launch via intent.
- Measure time to first paint of main screen.
- Assert: ≤ 500ms perceived latency.
- Assert: `SessionStore.current()` ещё не завершился к моменту первого paint.

Если test fails — значит F-4 блокирует cold-start path (FR-035 violated).

---

## Detekt rules (fitness functions)

Полный список rules в `config/detekt/auth-rules.yml`:

1. `NoVendorImportsInDomain` — `core/domain/auth/` чист от vendor SDK.
2. `NoVendorImportsInConsumers` — `core/config/`, `core/pairing/`, `app/sos/`, `app/photos/` чисты от auth SDK.
3. `NoFakeInRelease` — `FakeAuthAdapter` / `FakeSessionStore` не импортируются в `:app:release`.
4. `NoAnonymousAuth` — `signInAnonymously` не существует в codebase.
5. `OAuthScopeWhitelist` — Credential Manager request только `openid email profile`.
6. `NoPIIInAuthLog` — `Log.*` calls в `auth/` package не передают `AuthIdentity` / `SessionRecord` / `String token`.

Run:
```powershell
./gradlew detekt
```

Все 6 rules must pass green в CI.

---

## Common issues

### «Firebase Auth не работает в эмуляторе»
- Verify `firebase emulators:start` running.
- Verify `FirebaseEmulatorRule` connected к localhost:9099 (см. test setup).
- Verify `google-services.json` не использует production project ID в test builds.

### «Sign-in возвращает ProviderUnavailable на эмуляторе»
- Эмулятор должен иметь Google Play Services. Use AVD image `Google Play` (не «Google APIs», не «Android Open Source»).

### «Cold-start latency test fails локально, проходит в CI»
- Local dev build может иметь slower startup из-за Compose compiler hot-reload. Use release build для accurate measurement: `./gradlew :app:installRelease`.

### «Sign-in работает в debug build, не работает в release»
- Verify release SHA-1 fingerprint added в Firebase Console.
- Verify ProGuard rules для `androidx.credentials` + Firebase Auth (если obfuscation нарушает reflection).

### «ColdStartLatencyTest падает с timeout»
- Проверить что `Application.onCreate()` не делает blocking I/O в F-4 init. `AuthProvider` инициализация должна быть только DI registration; SessionStore read — в background coroutine after first paint.

---

## CI configuration

Reference: `.github/workflows/android-tests.yml` (или whatever CI system project uses).

Required steps:
1. Setup JDK 21.
2. Setup Android SDK.
3. Cache Gradle dependencies.
4. **Start Firebase Emulator Suite** (background).
5. Run unit tests: `./gradlew test`.
6. Run Detekt: `./gradlew detekt`.
7. Run instrumentation tests on AVD: `./gradlew connectedDebugAndroidTest`.
8. Upload test reports / coverage.

---

## Pre-release checklist (before F-4 ships в production)

Per FR-032 + plan.md «Rollout / Verification»:

- [ ] Firebase Auth project configured (production), Google provider enabled.
- [ ] SHA-1 fingerprints (debug + release) added.
- [ ] OAuth Consent Screen reviewed by Google (1-2 weeks process). Scopes locked to `openid email profile`.
- [ ] Firestore Security Rules deployed (identity-links + users — см. [contracts/firestore-security-rules.md](contracts/firestore-security-rules.md)).
- [ ] Privacy Policy updated с email / displayName collection statement.
- [ ] Play Console Data Safety form prepared (см. checklist-core-quality CHK012 для entries).
- [ ] `data_extraction_rules.xml` updated (exclude `auth_session_v1.preferences`).
- [ ] Crashlytics dashboard configured: `auth.error.*` categories tracked.
- [ ] Manual smoke на real device + real Google account (3 scenarios: новый аккаунт, существующий, отмена).

---

## Onboarding for new developer

**Mental model в 5 минут**:

1. **F-4 = identity layer**. Узнаёт «кто пользователь», ничего больше.
2. **Domain port `AuthProvider`** в `core/domain/auth/` — абстрактный интерфейс. Никаких Firebase / Google слов.
3. **Один real adapter** `GoogleSignInAuthAdapter` в `app/androidMain/auth/`. Здесь живут все Firebase / Google имена.
4. **Internal types** (`SessionRecord`, `SessionStore`) — consumer'ы их не видят. Только adapter.
5. **`SignInTrigger` composable** — переиспользуемый UI block для входа/выхода. Сейчас дёргает wizard; в будущем — Settings.

**Key invariants**:
- `AuthIdentity.stableId` = **наш UUID**, не Firebase UID, не Google sub claim (clarification Q1).
- F-4 после sign-in **не вызывает** config-sync (clarification Q7) — другие сервисы подписаны на `currentUser` flow и реагируют сами.
- Cold-start UI рисуется **до** F-4 init (FR-035).
- Локальный кеш конфигов **остаётся** при signOut (clarification Q3).

**First task для нового разработчика**: написать handler для нового AuthError variant.
1. Add new case в `AuthError` sealed (`core/domain/auth/AuthError.kt`).
2. Add handling в `SignInTrigger` (`Error` state branch).
3. Add inline error message в `strings_auth.xml`.
4. Add unit test в `AuthErrorHandlingTest.kt`.
5. Run Detekt: должен проходить.
6. Run unit tests: должны проходить.

Total time: ~1 hour для первой задачи.

---

## References

- [spec.md](spec.md) — full feature specification.
- [plan.md](plan.md) — architecture + Constitution Check.
- [research.md](research.md) — 10 architectural decisions с alternatives.
- [data-model.md](data-model.md) — все Kotlin types F-4.
- [contracts/](contracts/) — wire format + Security Rules contracts.
- CLAUDE.md — rules 1, 2, 4, 5, 6, 8 (центральные для F-4).
- F-CRYPTO spec 016 — analog для primitives module (similar mock-first pattern).

---

## TL;DR для не-разработчика

Этот документ — **инструкция для нового разработчика** которого нанимают через год работать с F-4.

**5-минутный onboarding**: что такое F-4, какие правила нельзя нарушать, как впервые запустить тесты.

**Главные практические шаги**:
1. Установить JDK 21 + Android Studio + эмулятор `pixel_5_api_34`.
2. Установить Firebase CLI (бесплатно, для локальных тестов без реального Firebase проекта).
3. Запустить тесты разных уровней (быстрые unit-тесты, медленные integration-тесты).
4. Запустить 6 автоматических проверок (Detekt) — они гарантируют, что Firebase / Google не «утекли» в неправильные места.

**Перед релизом** — 9 пунктов pre-release checklist (настройка Firebase, Privacy Policy, Play Console форма, исключение бэкапа). Это admin-задачи, не разработчик.

**Решение типичных проблем** — раздел «Common issues» с 5 примерами (что делать если эмулятор не подключается, если cold-start не проходит и т.д.).

**Первая задача для онбординга**: добавить новый вариант ошибки входа. Занимает ~1 час и проходит через все типичные места кода.
