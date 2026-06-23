# Implementation Plan: TASK-49 — Cloud Availability Boolean + SignIn Explanation + SOS Local Alternative

**Branch**: `task-49-cloud-feature-inventory-offline-first` | **Date**: 2026-06-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification из `specs/task-49-cloud-feature-inventory-offline-first/spec.md`

## Summary

Минимальная инфраструктура для разделения приложения на local-only / cloud режимы. Один булев `cloudAvailable` в DataStore, переключается **только** через push-events `AuthProvider.currentUser` (TASK-3, existing port). Реализуем `LocalAlternative` interface + `SOSDialerAlternative` как образец, переиспользуемый `SignInExplanationScreen` Composable, regression fix TASK-5 FCM token registration.

**Effort**: Small (~3-5 дней).

## Technical Context

**Language/Version**: Kotlin 2.x (KMP), Compose Multiplatform.
**Primary Dependencies**: `androidx.datastore:datastore-preferences`, existing `core/` ports (`AuthProvider`), Android `Intent.ACTION_DIAL`, `TelephonyManager`.
**Storage**: Android `Preferences DataStore` для persistent boolean. Никаких wire formats / external data flows.
**Testing**: kotlinx-coroutines-test для Flow tests, JVM unit tests, instrumented Android tests на `pixel_5_api_34` emulator.
**Target Platform**: Android API 26+ (existing minSdk проекта), KMP-ready через `commonMain` для будущего iOS.
**Project Type**: Mobile (Android-first, KMP architecture per existing).
**Performance Goals**: `cloudAvailable` read < 10ms (DataStore native), SOS dialer open < 1s (SC-002), Sign-In propagation < 500ms (SC-005/006).
**Constraints**: zero network calls в TASK-49 code, zero polling, only push-events от `AuthProvider`. Must not break TASK-5 / spec 018 / spec 019 на upgrade existing users.
**Scale/Scope**: 1 новый Gradle module (`:core:cloud`), 3 порта, 3 adapter'а, 1 Composable, ~600-800 LOC total.

## Project Structure

### Documentation (this feature)

```text
specs/task-49-cloud-feature-inventory-offline-first/
├── plan.md              # This file
├── spec.md              # Already exists (clarified)
├── research.md          # Phase 0 output — research one-way doors
├── data-model.md        # Phase 1 output — DataStore key shape
└── contracts/           # Phase 1 output — CloudAvailability + LocalAlternative + EmergencyNumberResolver contracts
    ├── cloud-availability-port.md
    ├── local-alternative-port.md
    └── emergency-number-resolver-port.md
```

Не создаём `quickstart.md` — нет нового dev workflow / build setup. Регрессионный fix TASK-5 — обычный edit existing code.

### Source Code (repository root)

```text
core/cloud/                                    # NEW Gradle module
├── build.gradle.kts                           # KMP setup, commonMain + androidMain
└── src/
    ├── commonMain/kotlin/com/launcher/cloud/
    │   ├── api/
    │   │   ├── CloudAvailability.kt           # port: isCloudAvailable() + isCloudAvailableFlow
    │   │   ├── LocalAlternative.kt            # opt-in interface
    │   │   ├── EmergencyNumberResolver.kt     # port для SOS dialer
    │   │   ├── ActionContext.kt               # data type
    │   │   └── ActionResult.kt                # data type
    │   └── fake/
    │       ├── FakeCloudAvailability.kt       # in-memory для тестов
    │       └── FakeEmergencyNumberResolver.kt # для тестов
    ├── commonTest/kotlin/com/launcher/cloud/
    │   └── contracts/
    │       ├── CloudAvailabilityContractTest.kt
    │       ├── LocalAlternativeContractTest.kt
    │       └── EmergencyNumberResolverContractTest.kt
    └── androidMain/kotlin/com/launcher/cloud/impl/
        ├── CloudAvailabilityImpl.kt           # DataStore + AuthProvider subscription
        ├── EmergencyNumberResolverImpl.kt     # TelephonyManager + fallback map
        └── SOSDialerAlternative.kt            # LocalAlternative реализация для SOS

app/src/main/kotlin/com/launcher/app/
├── onboarding/                                # NEW package
│   └── SignInExplanationScreen.kt             # переиспользуемый Composable
└── di/
    └── CloudModule.kt                         # Koin DI: bind CloudAvailability + adapters

# Modifications:
workers/push/                                  # NO changes — Worker сам не трогает FCM token
app/src/main/kotlin/com/launcher/app/auth/    # MODIFY: FCM token registration site
    FcmTokenRegistrationGuard.kt              # NEW wrapper / MODIFY existing call
                                                # → проверяет cloudAvailable перед persist в Firestore
```

**Structure Decision**: Новый Gradle модуль `:core:cloud` (KMP) per Constitution Article V (Modularization With Restraint) — это **первый consumer** будущих cloud-фич Phase 2, оправдывает separation. `SignInExplanationScreen` живёт в `app/.../onboarding/` (Compose UI, не KMP, переиспользуется wizard'ом TASK-7 и Settings cloud-actions).

## Architecture

### Module map

```
:app                              (Android UI + DI)
   ↓ depends on
:core:cloud                       NEW module (KMP commonMain + androidMain)
   ↓ depends on
:core                             (existing — AuthProvider port lives here)
   ↓ depends on
kotlin-stdlib + coroutines        (only)
```

`:core:cloud` **не зависит** от Firebase, Android SDK, ConnectivityManager, GoogleApiAvailability. Это **изоляция**, которая позволяет swap'нуть AuthProvider implementation (FirebaseAuth → OwnServerAuthProvider в будущем) без изменений `:core:cloud`.

### Data flow (push Observer pattern)

```
AuthProvider.currentUser: Flow<AuthIdentity?>      (TASK-3, existing)
        │
        │ emit identity / null
        ▼
CloudAvailabilityImpl                               (NEW, androidMain)
   ├── observes flow (init { scope.launch { collect } })
   ├── identity != null → DataStore.edit { cloudAvailable = true }
   └── identity == null → DataStore.edit { cloudAvailable = false }
        │
        │ DataStore exposes own Flow
        ▼
CloudAvailability.isCloudAvailableFlow              (port API)
        │
        │ collected by
        ▼
Cloud-features (TASK-5 FCM, future S-task'и)        (consumers)
```

**Ключевое**: `AuthProvider` не знает про `CloudAvailability` (правильное направление зависимости). `CloudAvailability` не знает про cloud-фичи (они её слушают).

### CloudAvailability API contract

```kotlin
package com.launcher.cloud.api

interface CloudAvailability {
    /** Синхронное чтение текущего флага. < 10ms (DataStore native read). */
    suspend fun isCloudAvailable(): Boolean

    /** Reactive подписка. Эмитит при изменении флага через AuthProvider events. */
    val isCloudAvailableFlow: Flow<Boolean>
}
```

### LocalAlternative API contract

```kotlin
package com.launcher.cloud.api

/**
 * Opt-in pattern для критических фич с локальным fallback.
 * Реализуется только теми фичами, которые ДОЛЖНЫ работать без cloud.
 * НЕ обязательный для всех cloud-фич.
 */
interface LocalAlternative {
    suspend fun executeLocally(context: ActionContext): ActionResult
}

data class ActionContext(
    val callerId: String,
    val parameters: Map<String, String> = emptyMap()
)

sealed class ActionResult {
    data class Success(val message: String? = null) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
}
```

### EmergencyNumberResolver API contract

```kotlin
package com.launcher.cloud.api

interface EmergencyNumberResolver {
    /** Возвращает первый emergency-номер для текущей locale устройства. */
    suspend fun getEmergencyNumber(): String
}
```

Android impl: `TelephonyManager.getCurrentEmergencyNumberList()` (API 29+) → первый item. Fallback (API < 29): hardcoded map по `Locale.getDefault().country` (`RU=102, US=911, GB/EU=112, IN=112, JP=110, AU=000`, default=`112`).

### CloudAvailabilityImpl pseudocode

```kotlin
class CloudAvailabilityImpl(
    private val dataStore: DataStore<Preferences>,
    private val authProvider: AuthProvider,
    private val scope: CoroutineScope,
) : CloudAvailability {

    private val key = booleanPreferencesKey("cloud_available")

    init {
        // Subscribe to AuthProvider on creation (DI singleton lifecycle).
        scope.launch {
            authProvider.currentUser.collect { identity ->
                dataStore.edit { prefs ->
                    prefs[key] = (identity != null)
                }
            }
        }
    }

    override suspend fun isCloudAvailable(): Boolean =
        dataStore.data.map { it[key] ?: false }.first()

    override val isCloudAvailableFlow: Flow<Boolean> =
        dataStore.data.map { it[key] ?: false }.distinctUntilChanged()
}
```

### SignInExplanationScreen Composable

Параметры:
- `onSignInClicked: () -> Unit` — caller тригерит Sign-In flow.
- `onCancelClicked: () -> Unit` — caller обрабатывает отмену.

Содержит: заголовок + 3-4 пункта (generic — резервная копия / удалённая настройка / синхронизация) + 2 кнопки. Все строки через string resources (i18n via TASK-1).

### FCM regression fix (TASK-5)

Locate site где TASK-5 регистрирует FCM token в Firestore. Wrap в:

```kotlin
class FcmTokenRegistrationGuard(
    private val cloudAvailability: CloudAvailability,
    private val firestoreFcmRegistrar: FirestoreFcmRegistrar, // existing
) {
    suspend fun registerIfAllowed(token: String) {
        if (cloudAvailability.isCloudAvailable()) {
            firestoreFcmRegistrar.register(token)
        }
        // else: token noted in memory by Firebase SDK, but NOT persisted to Firestore
    }
}
```

Также: observer на `isCloudAvailableFlow` — при transition `false → true` (новый Sign-In) → trigger registration текущего FCM token (если он есть в Firebase SDK cache).

## Data Model

См. [data-model.md](./data-model.md).

**Short**: единственная "data" entity — DataStore preference `cloud_available: Boolean`. Не wire format, не persistent across major versions с migration. Не требует `schemaVersion` per CLAUDE.md rule 5.

## Wire Formats

**None.** TASK-49 не вводит wire format'ов:
- DataStore preference — internal cache, не leaves device.
- `ActionContext` / `ActionResult` — runtime types, не serialized.
- `SignInExplanationScreen` strings — i18n resources, не wire.

## Dependency Impact

**Новые Gradle dependencies:**

| Dependency | Version | Module | Justification (Article XIII) |
|---|---|---|---|
| `androidx.datastore:datastore-preferences` | 1.1.x (existing? check) | `core:cloud:androidMain` | Persistent boolean storage. Уже может быть в проекте (check existing usage). |

**Существующие dependencies (используем, не добавляем):**
- `kotlinx.coroutines` (existing) — Flow / StateFlow.
- `core` module (existing) — для `AuthProvider`.
- `androidx.compose.*` (existing) — для `SignInExplanationScreen`.
- Android SDK `TelephonyManager`, `Intent.ACTION_DIAL` — system API.

**Проверить при /speckit.tasks:**
- Уже подключён ли DataStore в `:app` или каком-либо `core:*` модуле. Если да — переиспользовать; если нет — добавить только в `core:cloud:androidMain`.

## Test Strategy

Per CLAUDE.md rule 6 (mock-first) + rule 7 (fitness functions):

### Contract tests (`core:cloud:commonTest`)

- **`CloudAvailabilityContractTest`**: проверяет invariants port'а — read returns last value, Flow emits on change, distinctUntilChanged.
- **`LocalAlternativeContractTest`**: проверяет `executeLocally` returns deterministic `ActionResult` без побочных эффектов в domain.
- **`EmergencyNumberResolverContractTest`**: invariants — всегда возвращает non-empty string.

### Fake adapters (`core:cloud:commonMain/fake/`)

- **`FakeCloudAvailability`** — in-memory, controllable boolean + Flow для тестов consumers.
- **`FakeEmergencyNumberResolver`** — returns fixed test number.
- **`FakeAuthProvider`** — existing in `core/commonTest`, переиспользуем.

### Unit tests (`core:cloud:androidUnitTest`)

- `CloudAvailabilityImpl` подписка на `FakeAuthProvider` → emit identity → boolean flips → verify.
- `CloudAvailabilityImpl` emit null → boolean → false.
- `CloudAvailabilityImpl` после reboot (recreate instance) → boolean читается из DataStore без пересчёта.
- `EmergencyNumberResolverImpl` fallback — mock `TelephonyManager` returns null → use map fallback.
- `SOSDialerAlternative.executeLocally()` builds `Intent.ACTION_DIAL` с правильным URI.

### Integration tests (`app:androidTest`, instrumented на `pixel_5_api_34`)

- **`OfflineFirstE2ETest`**: fresh install → main screen без Sign-In → проверить `cloudAvailable=false` в DataStore.
- **`SignInFlowE2ETest`**: trigger Sign-In → `cloudAvailable=true` записан → reboot emulator → флаг сохраняется.
- **`SOSLocalAlternativeE2ETest`**: тап SOS → Intent.ACTION_DIAL launched < 1s, независимо от `cloudAvailable`.
- **`SignInExplanationScreenE2ETest`**: показ экрана + кнопки + строки локализованы.
- **`CloudConfigEncryptionE2ETest`** (regression spec 018): ciphertext round-trip preserved post-TASK-49 merge.
- **`FcmTokenTimingE2ETest`** (regression spec 019 + new FR-013): без Sign-In FCM token НЕ в Firestore; после Sign-In — token появляется.

### Fitness functions

- **Gradle task `verifyCloudIsolation`** (per FR §7): `:core:cloud` Gradle dependencies = только `kotlin-stdlib` + `kotlinx.coroutines` + `androidx.datastore-preferences` (androidMain only). Никаких Firebase / Android SDK в `commonMain`.
- **Detekt rule `NoVendorImportsInDomain`** (existing) — auto-applies к `:core:cloud:commonMain`.

### Physical device verification (`// TODO(physical-device)`)

- physical device #1 (currently Xiaomi 11T) — regression check spec 018 + spec 019 + FCM token timing.
- Future Huawei — NoOpRecoveryKeyBackup path (TASK-6 territory, не сейчас).

## Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | TASK-5 regression breaks existing users (FCM token дереgister'ся unexpected) | FR-014 explicit: existing tokens NOT touched. Integration test `FcmTokenTimingE2ETest` проверяет: post-upgrade existing token preserved. |
| R2 | `AuthProvider.currentUser` Flow поведение не consistent (повторные emits, race conditions) | Contract `auth-provider-port.md` (TASK-3) уже specs distinct-until-changed. Verify в тестах CloudAvailabilityImpl. |
| R3 | DataStore corruption / migration / version conflicts | DataStore IO failures → fallback на default false. Без data migration (single boolean, no schema). |
| R4 | Sign-In flow в Compose теряет state при rotation device | `SignInExplanationScreen` stateless (caller передаёт callbacks). State holding — в caller'е через `rememberSaveable`. |
| R5 | Существующие cloud-фичи не учитывают `cloudAvailable` после TASK-49 merge | Documentation FR-015 + convention. Consumers Phase 2 будут писаться **после** TASK-49 → они уже учтут. |
| R6 | OEM-specific DataStore behavior (Xiaomi background restrict) | DataStore IO синхронный, не background WorkManager. Background restrict не влияет. |
| R7 | `TelephonyManager.getCurrentEmergencyNumberList()` permission requirements | API 29+ требует `READ_PHONE_STATE`? Verify в research.md. Если да — fallback map работает без permission. |

## Required Context Review

Per Article XII §7 — relevant документы которые influence этот план:

### Decisions (must-read)
- [decision 2026-06-15-deferred-cloud/01](../../docs/product/decisions/2026-06-15-deferred-cloud/01-deferred-sign-in.md) — primary source принципа device-self-sufficiency.

### Engineering rules
- [CLAUDE.md](../../CLAUDE.md) — rules 1 (domain isolation), 2 (ACL), 3 (one-way doors — TASK-49 two-way), 4 (MVA — explicit отказ от CloudActionGate / CloudFeatureRegistry / CloudMode), 6 (mock-first), 7 (fitness functions), 8 (server migration tracking).

### Constitution
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Articles I (Architecture), V (Modularization), XIII (Dependencies justification).

### Existing specs (depend on)
- [TASK-3 / spec 017](../017-f4-auth-provider/spec.md) — AuthProvider port contract.
- [TASK-5 / spec 019](../019-f5c-fcm-config-updated/spec.md) — FCM registration site, regression scope.
- [TASK-6 / spec 020](../020-f5-root-key-hierarchy-recovery/spec.md) — Paused, ждёт TASK-49 для CloudActionGate replacement.

### Memory references
- `project_deferred_cloud_architecture` — общая стратегия.
- `project_spec_017_018_019_e2e_working` — regression baseline.
- `feedback_organic_question_budgets` — clarify methodology.

### Server roadmap (for inline TODO)
- [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) — добавить inline TODO в `CloudAvailabilityImpl`: «при переезде на own server — `OwnServerAuthProvider` adapter реализует тот же `AuthProvider.currentUser` Flow, TASK-49 не меняется».

## Constitution Check

**Runtime check via `procedure-constitution-check` skill** — будет запущена отдельным шагом (Step 4) и report'ом inlined обратно сюда.

Pre-check inline (моя assessment перед formal проверкой):

| Gate | Status | Notes |
|---|---|---|
| Architecture (Article I) | ✅ PASS | Port-adapter shape соблюдён. `:core:cloud` изолирован от vendor SDK. |
| Core/System Integration (Article IV) | ✅ PASS | `AuthProvider` уже существует; reuse. DataStore — стандартный Android API. |
| Configuration (Article VII) | ✅ PASS | DataStore single boolean — простейшая конфигурация. |
| Required Context Review (Article XII) | ✅ PASS | Section выше cover все relevant docs. |
| Accessibility (Article VIII) | ⚠️ TO VERIFY | `SignInExplanationScreen` — Compose UI с кнопками. Нужно проверить через `checklist-accessibility` skill: TalkBack labels, focus order, tap target ≥56dp (senior-safe). |
| Battery/Performance (Article IX) | ✅ PASS | Zero polling, zero background. DataStore IO synchronous < 10ms. SOS dialer < 1s. |
| Testing (rule 6 + Article X) | ✅ PASS | Contract + fake + integration + fitness. Mock AuthProvider existing. |
| Simplicity (Article XI) | ✅ PASS | Explicit отказ от CloudActionGate / CloudFeatureRegistry per owner mandate. MVA соблюдён. |

## Rollout / Verification

### Pre-merge checks

1. **JVM unit tests** все зелёные: `./gradlew :core:cloud:jvmTest`.
2. **Android unit tests**: `./gradlew :core:cloud:androidUnitTest`.
3. **Integration tests на эмуляторе**: `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="*OfflineFirst*|*SignInFlow*|*SOSLocal*|*SignInExplanation*"`.
4. **Regression integration на эмуляторе**: `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="*CloudConfigEncryption*|*FcmTokenTiming*"`.
5. **Fitness function**: `./gradlew :core:cloud:verifyCloudIsolation`.
6. **Detekt**: `./gradlew detekt`.

### Physical device verification (mandatory pre-merge)

per `// TODO(physical-device)`:
- physical device #1 (currently Xiaomi 11T):
  - Install upgrade APK поверх existing spec 019 install.
  - Verify FCM token preserved в Firestore (regression).
  - Verify spec 018 ciphertext round-trip passes.
  - Verify new install без Sign-In → FCM token НЕ в Firestore.
  - Verify Sign-In → FCM token зарегистрирован.

### Smoke checkpoints (manual)

- Fresh install → wizard без Sign-In → main screen → SOS открывает dialer.
- Settings → cloud-action mock → `SignInExplanationScreen` появляется → Sign-In → `cloudAvailable=true`.
- Sign-Out из Settings → `cloudAvailable=false` в DataStore.
- Reboot emulator → флаг сохранён.

### Documentation deliverable

`docs/dev/cloud-availability.md` написана на простом русском, читается non-developer владельцем за <10 минут (per SC-010).

### Backlog sync

После merge: TASK-49 → Done. TASK-6 → resume from Paused (готовый trigger для FR-008 «первый cloud-action» = читать `cloudAvailable`).

---

## Plain Russian summary (для не-разработчика владельца)

**Что в этом плане**: пошаговая схема как мы будем строить TASK-49 (булев флаг + объяснительный экран + SOS fallback + regression fix FCM).

**Структура кода**:
- Новый модуль `core/cloud/` (multiplatform-готовый, для будущего iOS).
- Внутри: 3 контракта (`CloudAvailability`, `LocalAlternative`, `EmergencyNumberResolver`), 3 реализации, 1 экран (`SignInExplanationScreen` в `app/onboarding/`).
- Один guard класс который проверяет cloudAvailable перед регистрацией FCM token (regression fix TASK-5).

**Как работает**:
- `AuthProvider` (уже сделан в TASK-3) даёт Flow «есть identity / нет identity».
- Наш `CloudAvailabilityImpl` подписан, обновляет булев в DataStore.
- Все cloud-фичи читают булев через `isCloudAvailable()` или `isCloudAvailableFlow`.

**Что НЕ делаем**: проверки сети, GMS, token refresh, CloudActionGate, CloudFeatureRegistry — всё это out of scope по решению owner'а 2026-06-23.

**Тестирование**: contract tests + fake adapters + emulator instrumentation + regression на physical Xiaomi 11T.

**Риски и mitigation**: 7 рисков задокументированы (главный — FCM regression, проверяется отдельным test'ом).

**Effort**: 3-5 дней.

**После merge**: TASK-6 возобновляется с готовым trigger'ом (`cloudAvailable` flag).
