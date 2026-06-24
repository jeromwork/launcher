# Tasks: TASK-49 — Cloud Availability Boolean + SignIn Explanation + SOS Local Alternative

**Input**: Design documents from `/specs/task-49-cloud-feature-inventory-offline-first/`
**Prerequisites**: [plan.md](plan.md) ✅ (Constitution Check PASS), [spec.md](spec.md) ✅ (Clarified, 8 Q-A), [data-model.md](data-model.md) ✅, [contracts/](contracts/) ✅ (3 port contracts).

**Tests**: Тесты обязательны per CLAUDE.md rule 6 (mock-first) + rule 7 (fitness functions) + Article X. Каждый port имеет fake adapter, каждый contract имеет invariant tests.

**Organization**: Tasks grouped по 6 фазам, каждая фаза → US/FR coverage:
- Phase 1: Setup (новый Gradle module `:core:cloud` + dependencies).
- Phase 2: Domain types + ports (commonMain).
- Phase 3: Fakes + contract tests (commonTest + commonMain/fake/).
- Phase 4: Adapters (androidMain implementations + unit tests).
- Phase 5: UI + integration (SignInExplanationScreen + DI wiring + FCM regression).
- Phase 6: Verification + docs + cleanup (instrumented tests + docs + fitness + smoke).

**Format**: `[ID] [P?] Description (trace FR/US/Plan)` — `[P]` = можно делать параллельно с другими `[P]` той же фазы.

**Path conventions**: KMP project (`core/cloud/commonMain/`, `core/cloud/commonTest/`, `core/cloud/androidMain/`) + Android UI (`app/src/main/kotlin/com/launcher/app/onboarding/`, `app/.../di/`, `app/.../auth/`).

**Owner**: единичный исполнитель, последовательная работа по фазам. `[P]` markers сохранены для будущей team-сборки.

---

## Phase 1: Setup (Gradle module + dependencies)

**Purpose**: Создать пустой module `:core:cloud` с правильным KMP setup, без бизнес-логики.

- [x] **T001** Создать Gradle module `core/cloud/` (`build.gradle.kts` с KMP plugin, target Android + JVM, dependencies: `kotlin-stdlib`, `kotlinx-coroutines-core`, для androidMain — `androidx.datastore:datastore-preferences`). (Plan §Project Structure). **Acceptance**: `./gradlew :core:cloud:assemble` зелёный.
- [x] **T002** Зарегистрировать module `:core:cloud` в `settings.gradle.kts`. **Acceptance**: `./gradlew projects` показывает `:core:cloud`.
- [x] **T003** [P] Добавить dependency `:core` (existing module) в `:core:cloud` (для доступа к `AuthProvider` port). **Acceptance**: `./gradlew :core:cloud:dependencies` показывает `:core` в compileClasspath.
- [x] **T004** [P] Создать пакетную структуру `core/cloud/src/{commonMain,commonTest,androidMain}/kotlin/com/launcher/cloud/{api,fake,impl,contracts}/` (пустые placeholder .gitkeep файлы где надо).

---

## Phase 2: Domain types + ports (commonMain)

**Purpose**: Pure-Kotlin port interfaces + data types без implementations.

- [x] **T005** Создать `core/cloud/src/commonMain/kotlin/com/launcher/cloud/api/CloudAvailability.kt` — interface с `suspend fun isCloudAvailable(): Boolean` + `val isCloudAvailableFlow: Flow<Boolean>`. KDoc описывает invariants INV-1..INV-7 из contract. (FR-001, contracts/cloud-availability-port.md). **Acceptance**: compile без ошибок.
- [x] **T006** [P] Создать `core/cloud/src/commonMain/kotlin/com/launcher/cloud/api/LocalAlternative.kt` — interface с `suspend fun executeLocally(context: ActionContext): ActionResult`. KDoc описывает opt-in pattern + invariants INV-1..INV-4. (FR-009, contracts/local-alternative-port.md). **Acceptance**: compile.
- [x] **T007** [P] Создать `core/cloud/src/commonMain/kotlin/com/launcher/cloud/api/EmergencyNumberResolver.kt` — interface с `suspend fun getEmergencyNumber(): String`. KDoc invariants INV-1..INV-7. (FR-011, contracts/emergency-number-resolver-port.md). **Acceptance**: compile.
- [x] **T008** [P] Создать `core/cloud/src/commonMain/kotlin/com/launcher/cloud/api/ActionContext.kt` — `data class ActionContext(callerId: String, parameters: Map<String, String> = emptyMap())`. (Key Entities). **Acceptance**: compile.
- [x] **T009** [P] Создать `core/cloud/src/commonMain/kotlin/com/launcher/cloud/api/ActionResult.kt` — `sealed class` с `Success(message: String?)` и `Failure(reason: String)`. (Key Entities). **Acceptance**: compile.

---

## Phase 3: Fakes + contract tests

**Purpose**: Fake adapters для тестов consumers + contract tests verifying port invariants.

### Fakes

- [x] **T010** Создать `core/cloud/src/commonMain/kotlin/com/launcher/cloud/fake/FakeCloudAvailability.kt` — in-memory `MutableStateFlow<Boolean>`, manual `set(value: Boolean)` API для тестов. (Plan §Test Strategy). **Acceptance**: compile + `set(true) → isCloudAvailable() returns true`.
- [x] **T011** [P] Создать `core/cloud/src/commonMain/kotlin/com/launcher/cloud/fake/FakeEmergencyNumberResolver.kt` — конструктор принимает fixed number, `getEmergencyNumber()` возвращает его. **Acceptance**: compile.
- [x] **T012** [P] Создать `core/cloud/src/commonMain/kotlin/com/launcher/cloud/fake/FakeLocalAlternative.kt` — конструктор принимает predefined `ActionResult`, `executeLocally()` возвращает его. **Acceptance**: compile.

### Contract tests

- [x] **T013** Создать `core/cloud/src/commonTest/kotlin/com/launcher/cloud/contracts/CloudAvailabilityContractTest.kt`. Test cases: INV-1 (read returns current), INV-3 (flow emits initial), INV-4 (flow emits on change), INV-5 (distinctUntilChanged), INV-6 (default false), INV-7 (persistence survives recreate — для FakeCloudAvailability проверяется через external state). (contracts/cloud-availability-port.md). **Acceptance**: 6 unit-test'ов зелёные.
- [x] **T014** [P] Создать `core/cloud/src/commonTest/kotlin/com/launcher/cloud/contracts/LocalAlternativeContractTest.kt`. Test cases: INV-1 (no cloud dependency — проверяется через FakeLocalAlternative + null CloudAvailability), INV-3 (no exceptions). (contracts/local-alternative-port.md). **Acceptance**: tests зелёные.
- [x] **T015** [P] Создать `core/cloud/src/commonTest/kotlin/com/launcher/cloud/contracts/EmergencyNumberResolverContractTest.kt`. Test cases: INV-1 (non-empty), INV-2 (valid phone format). (contracts/emergency-number-resolver-port.md). **Acceptance**: tests зелёные.

---

## Phase 4: Android adapters (androidMain implementations + unit tests)

**Purpose**: Реальные Android implementations port'ов.

### CloudAvailabilityImpl

- [x] **T016** Создать `core/cloud/src/androidMain/kotlin/com/launcher/cloud/impl/CloudAvailabilityImpl.kt` per pseudocode из plan.md §Architecture. Конструктор принимает `DataStore<Preferences>` + `AuthProvider` + `CoroutineScope`. Internal key `cloud.availability.is_available` (namespaced per wire-format checklist CHK013). (FR-001, FR-002, FR-003, FR-004, FR-005). **Acceptance**: compile.
- [x] **T017** Создать `core/cloud/src/androidUnitTest/kotlin/com/launcher/cloud/impl/CloudAvailabilityImplTest.kt`. Использует `FakeAuthProvider` (existing in `core/commonTest`) + test DataStore (in-memory или temp file). Tests:
  - `auth provider emit identity → cloudAvailable=true в DataStore` за <500ms (SC-005).
  - `auth provider emit null → cloudAvailable=false` за <500ms (SC-006).
  - `recreate impl instance → последнее значение читается из DataStore` (INV-7 / SC-004).
  - `distinctUntilChanged: повторный emit того же identity → flow не emit'ит дубль` (INV-5).
  (FR-003, SC-004/005/006). **Acceptance**: 4 unit-test'а зелёные.

### EmergencyNumberResolverImpl

- [x] **T018** [P] Создать `core/cloud/src/androidMain/kotlin/com/launcher/cloud/impl/EmergencyNumberResolverImpl.kt`. Конструктор принимает `TelephonyManager` + locale provider lambda (`() -> Locale`). API 29+ путь: `TelephonyManager.getCurrentEmergencyNumberList()` → first item. Fallback: hardcoded map (RU/BY/KZ=102, US/CA=911, GB/EU/IN=112, JP=110, AU=000, CN=110, default=112) — per contract emergency-number-resolver-port.md. (FR-011). **Acceptance**: compile.
- [x] **T019** Создать `core/cloud/src/androidUnitTest/kotlin/com/launcher/cloud/impl/EmergencyNumberResolverImplTest.kt`. Mock TelephonyManager. Tests:
  - `RU locale + API < 29 (TelephonyManager returns null) → returns "102"` (INV-3).
  - `US locale + API < 29 → returns "911"` (INV-4).
  - `EU locale (DE) + API < 29 → returns "112"` (INV-5).
  - `API 29+ TelephonyManager returns ["112"] → returns "112"` (INV-6).
  (FR-011). **Acceptance**: 4 tests зелёные.

### SOSDialerAlternative

- [x] **T020** [P] Создать `core/cloud/src/androidMain/kotlin/com/launcher/cloud/impl/SOSDialerAlternative.kt`. Реализует `LocalAlternative`. Конструктор принимает `EmergencyNumberResolver` + `Context` (для startActivity). `executeLocally()` строит `Intent(ACTION_DIAL, Uri.parse("tel:$number"))` с `FLAG_ACTIVITY_NEW_TASK`, вызывает `context.startActivity(intent)`, возвращает `ActionResult.Success`. (FR-010, FR-012). **Acceptance**: compile.
- [x] **T021** Создать `core/cloud/src/androidUnitTest/kotlin/com/launcher/cloud/impl/SOSDialerAlternativeTest.kt`. Mock Context + FakeEmergencyNumberResolver. Tests:
  - `executeLocally(callerId="sos") → builds Intent.ACTION_DIAL с tel:112 URI`.
  - `Returns ActionResult.Success`.
  - `Не делает cloud-проверок` (INV-1 — нет вызовов на CloudAvailability).
  (FR-010, FR-012, SC-002). **Acceptance**: 3 tests зелёные.

---

## Phase 5: UI + integration (SignInExplanationScreen + DI + FCM regression)

**Purpose**: Reusable Composable + DI wiring + regression fix TASK-5 FCM token registration.

### SignInExplanationScreen

- [x] **T022** Создать `app/src/main/kotlin/com/launcher/app/onboarding/SignInExplanationScreen.kt` — Composable function с параметрами `onSignInClicked: () -> Unit`, `onCancelClicked: () -> Unit`. Layout: Column с Title + 3-4 BulletPoint элементов + Spacer + Row с двумя кнопками. Все строки из string resources. Tap target кнопок ≥56dp. (FR-005, FR-006, FR-008, FR-008a). **Acceptance**: compile + manual preview в Android Studio.
- [x] **T023** Добавить string resources для `SignInExplanationScreen`: в `app/src/main/res/values/strings.xml` (или KMP resources directory согласно existing pattern) — keys `cloud.signin.explanation.title`, `cloud.signin.explanation.bullet1..bullet4`, `cloud.signin.explanation.button_signin`, `cloud.signin.explanation.button_cancel`. EN base + RU manually. (FR-007). **Acceptance**: strings present.
- [ ] **T024** Запустить `procedure-translate-spec-strings` (existing skill) для генерации остальных 9 locales (ES/ZH/AR/HI/PT/DE/FR/JA/KK-Latn). (FR-007). **Acceptance**: 11 locale-файлов содержат соответствующие keys.
- [ ] **T025** [deferred-local-emulator: composeUiTest 1.7.x vs API 35+ InputManager — needs AVD ≤ API 34. File written + compile зелёный, run deferred.] Создать `app/src/androidTest/kotlin/com/launcher/app/onboarding/SignInExplanationScreenE2ETest.kt` через Compose UI testing framework. Tests:
  - Composable рендерится без ошибок.
  - Title + 3-4 bullets visible.
  - Кнопки visible, tap target ≥56dp (через `onNode...assertHeightIsAtLeast(56.dp)`).
  - Click «Войти» → `onSignInClicked` invoked.
  - Click «Отмена» → `onCancelClicked` invoked.
  - TalkBack contentDescriptions present (через `assertContentDescriptionEquals`).
  (FR-005, FR-006, FR-008a). **Acceptance**: 6 instrumented tests зелёные на эмуляторе `pixel_5_api_34`.

### DI wiring

- [x] **T026** Создать `app/src/main/kotlin/com/launcher/app/di/CloudModule.kt` (Koin module или существующая DI convention). Bind:
  - `DataStore<Preferences>` — singleton, file `cloud_settings.preferences_pb`.
  - `CloudAvailability` → `CloudAvailabilityImpl` (singleton, передаёт DataStore + injected AuthProvider + applicationScope).
  - `EmergencyNumberResolver` → `EmergencyNumberResolverImpl` (singleton, передаёт TelephonyManager from system services).
  - `LocalAlternative` qualified `@Named("sos")` → `SOSDialerAlternative`.
  (Plan §Architecture DI wiring). **Acceptance**: compile + app starts без DI errors.

### FCM regression fix (TASK-5)

- [x] **T027** Найти site регистрации FCM token в Firestore (search `FirebaseMessaging` / `setMessagingToken` / spec 019 fcm-related классы). Document file path в этой task. **Acceptance**: file path identified.
- [x] **T028** Создать `app/src/main/kotlin/com/launcher/app/auth/FcmTokenRegistrationGuard.kt`. Wraps existing FCM Firestore registration site. Метод `suspend fun registerIfAllowed(token: String)`:
  - Проверяет `cloudAvailability.isCloudAvailable()` — если `false`, return без действий.
  - Если `true` — вызывает existing FCM registrar (через injected dependency).
  Также: observer на `isCloudAvailableFlow` — при transition `false → true` → trigger registration текущего FCM token. (FR-013, FR-014). **Acceptance**: compile.
- [x] **T029** Модифицировать FCM token registration call site (identified в T027) — заменить direct call на вызов через `FcmTokenRegistrationGuard`. Existing tokens NOT touched per FR-014. **Acceptance**: existing code refactored, tests старые проходят.
- [x] **T030** Создать `app/src/androidUnitTest/kotlin/com/launcher/app/auth/FcmTokenRegistrationGuardTest.kt`. Tests:
  - `cloudAvailable=false → registerIfAllowed(token) → no Firestore call`.
  - `cloudAvailable=true → registerIfAllowed(token) → Firestore registration invoked`.
  - `cloudAvailable transitions false→true с existing token → automatic registration triggered`.
  (FR-013). **Acceptance**: 3 unit-tests зелёные.

---

## Phase 6: Verification + docs + cleanup

**Purpose**: Instrumented E2E tests, fitness functions, documentation, regression checks.

### Instrumented integration tests (emulator)

> **[deferred-local-emulator]** T031–T036 + T043 откладываются до AVD API ≤34 на домашнем компьютере пользователя (composeUiTest 1.7.x не работает на API 35+; см. memory `reference_compose_ui_test_api_mismatch.md`). Code-only tasks (T037–T040, T042) идут в этой сессии.

- [ ] **T031** Создать `app/src/androidTest/kotlin/com/launcher/app/cloud/OfflineFirstE2ETest.kt`. Fresh install scenario:
  - Clear app data → launch app → проверить main screen появляется без Sign-In prompt.
  - `cloudAvailable` в DataStore = `false`.
  - 5 минут idle → `inspect Firestore` (через test fixture / mock backend) — нет registered FCM token для device.
  (US-1, SC-001, SC-003). **Acceptance**: instrumented test зелёный на `pixel_5_api_34`.
- [ ] **T032** Создать `app/src/androidTest/kotlin/com/launcher/app/cloud/SignInFlowE2ETest.kt`. Scenario:
  - Trigger Sign-In flow (mock AuthProvider success) → `cloudAvailable=true` за <500ms (SC-005).
  - Trigger Sign-Out → `cloudAvailable=false` за <500ms (SC-006).
  - Kill app + relaunch → `cloudAvailable` сохранён в DataStore (SC-004).
  (US-3, SC-004/005/006). **Acceptance**: instrumented test зелёный.
- [ ] **T033** [P] Создать `app/src/androidTest/kotlin/com/launcher/app/cloud/SOSLocalAlternativeE2ETest.kt`. Scenario:
  - `cloudAvailable=false` → тап SOS → Intent.ACTION_DIAL fired за <1 сек (SC-002).
  - Airplane mode ON → SOS still works.
  (US-2, SC-002). **Acceptance**: instrumented test зелёный.
- [ ] **T034** [P] Создать `app/src/androidTest/kotlin/com/launcher/app/cloud/FcmTokenTimingE2ETest.kt`. Regression test для FR-013:
  - Fresh install без Sign-In → 30 секунд idle → `cloudAvailable=false`, FCM token не записан в Firestore.
  - Sign-In → `cloudAvailable=true` → FCM token регистрируется автоматически за <30 секунд (через observer FR-013).
  (FR-013, FR-014). **Acceptance**: instrumented test зелёный.

### Regression checks (existing tests must pass)

- [ ] **T035** Запустить existing spec 018 round-trip test (`CloudConfigEncryptionE2ETest`) на эмуляторе → byte-equal результат до и после TASK-49 merge. (FR-014, regression). **Acceptance**: existing test passes.
- [ ] **T036** Запустить existing spec 019 FCM smoke test на эмуляторе → push доставляется как раньше (для existing-user scenario). (FR-014, regression). **Acceptance**: existing test passes.

### Fitness functions

- [x] **T037** Добавить Gradle task `verifyCloudIsolation` в `core/cloud/build.gradle.kts`. Проверяет:
  - `:core:cloud:commonMain` dependencies = только `kotlin-stdlib` + `kotlinx-coroutines-core` + `:core` (для AuthProvider).
  - `:core:cloud:androidMain` дополнительно может иметь `androidx.datastore:datastore-preferences`.
  - Никаких Firebase / Google Play Services / Android UI dependencies в `commonMain`.
  (Plan §Test Strategy, CLAUDE.md rule 7). **Acceptance**: `./gradlew :core:cloud:verifyCloudIsolation` зелёный.
- [x] **T038** [P] Verify через Detekt: `:core:cloud:commonMain` files passes `NoVendorImportsInDomain` rule (existing). **Acceptance**: `./gradlew :core:cloud:detekt` зелёный.

### Documentation

- [x] **T039** Создать `docs/dev/cloud-availability.md` на простом русском (per FR-015, SC-010):
  - Что такое `cloudAvailable` булев флаг.
  - Как меняется (только через `AuthProvider` events).
  - Что считать «первым cloud-action».
  - Как cloud-фича читает флаг (через `isCloudAvailable()` или `isCloudAvailableFlow`).
  - Convention для cloud-фич: подписаться на flow, обрабатывать transitions.
  - Что TASK-49 НЕ делает (network, GMS, token expiry).
  - **Явно**: «эта architecture — текущий snapshot. Через 6-12 месяцев может смениться без переписывания core».
  - Inline TODO про SRV-AUTH-001 (server-roadmap migration).
  (FR-015, FR-018, SC-010). **Acceptance**: file читается non-developer за <10 минут.
- [x] **T040** [P] Применить `procedure-add-novice-summary` skill к `docs/dev/cloud-availability.md` — добавить TL;DR в конец. (Convention). **Acceptance**: summary секция в конце файла.

### Physical device verification

> **[deferred-physical-device]** T041 откладывается — per memory `reference_testing_environment.md` реальное устройство не используется в AI-сессии. Inline-TODO `physical-device` оставляем; пользователь прогоняет вручную на Xiaomi 11T.

- [ ] **T041** Manual verification на physical device #1 (currently Xiaomi 11T):
  - Install upgrade APK поверх existing spec 019 install.
  - Verify FCM token preserved в Firestore (regression FR-014).
  - Verify spec 018 ciphertext round-trip passes (regression FR-014).
  - Verify new install без Sign-In → FCM token НЕ в Firestore (FR-013).
  - Verify Sign-In → FCM token регистрируется.
  (SC-007 + `// TODO(physical-device)` из spec). **Acceptance**: все 4 проверки passed, результаты записаны в commit message PR.

### Backlog sync (final)

- [x] **T042** После всех implementation tasks — проверить `[backlog]`-marked SC всё ещё актуальны после possible изменений в spec.md. Если изменились — запустить `procedure-sync-backlog-ac` для синхронизации TASK-49 AC. (Convention из speckit-tasks Step 4c). **Acceptance**: TASK-49 AC отражают current SC.

### Smoke + completion

- [ ] **T043** Manual smoke на `pixel_5_api_34` emulator: fresh install → wizard без Sign-In → main screen → SOS открывает dialer → Settings → trigger cloud-action (mock) → SignInExplanationScreen → mock Sign-In → cloudAvailable=true. **Acceptance**: все шаги работают, скриншоты приложены к PR.
- [ ] **T044** Обновить TASK-49 status в backlog → `Done` ПОСЛЕ merge PR в main. **Acceptance**: `backlog task edit task-49 -s Done`.
- [ ] **T045** [P] После TASK-49 closure — перевести TASK-6 из `Paused` обратно в `Draft` (или `In Progress` если сразу беремся). Обновить note в TASK-6 — убрать «ждёт TASK-49». (Post-merge action). **Acceptance**: TASK-6 status обновлён.

---

## Dependencies graph

```
Phase 1 (T001-T004): foundation, нет dependencies между ними после T002.
   ↓
Phase 2 (T005-T009): зависит от T001-T004. T005 → T006/T007/T008/T009 могут параллельно.
   ↓
Phase 3 (T010-T015): зависит от Phase 2. T010 [P] T011 [P] T012 → T013 [P] T014 [P] T015.
   ↓
Phase 4 (T016-T021): зависит от Phase 2 + 3.
   T016 → T017.
   T018 → T019.
   T020 (зависит от T018, потому что use EmergencyNumberResolver) → T021.
   ↓
Phase 5 (T022-T030):
   T022 → T023 → T024.
   T022 → T025.
   T026 (зависит от Phase 4).
   T027 → T028 → T029 → T030.
   ↓
Phase 6 (T031-T045):
   T031, T032, T033, T034 параллельно (зависят от Phase 5).
   T035, T036 регрессия (existing tests).
   T037, T038 fitness — параллельно остальным Phase 6.
   T039 → T040.
   T041 — physical device, после всего остального.
   T042 — sync backlog.
   T043 — smoke.
   T044, T045 — post-merge actions.
```

---

## Coverage trace

**Functional Requirements**:
- FR-001 (CloudAvailability port API): T005, T013, T017, T032.
- FR-002 (DataStore persist): T016, T032 (SC-004).
- FR-003 (subscribe AuthProvider, no other observers): T016, T017.
- FR-004 (no other observers): T037 (verifyCloudIsolation).
- FR-005 (no pre-recompute on app launch): T032 (verify через kill + relaunch).
- FR-006 (LocalAlternative interface): T006, T014.
- FR-007 (SOSDialerAlternative ACTION_DIAL): T020, T021, T033.
- FR-008 (EmergencyNumberResolver locale logic): T018, T019.
- FR-009 (CloudActionGate removed) → N/A (explicit out of scope per Clarifications Q5).
- FR-009/010/011 (SignInExplanationScreen reusable + content): T022, T023, T025.
- FR-008a (accessibility): T022 (tap target), T025 (TalkBack + assertions).
- FR-007 i18n strings: T023, T024.
- FR-008 reusability wizard+Settings: T022 + planned use in TASK-7.
- FR-013 (FCM regression fix): T027-T030, T034.
- FR-014 (existing tokens not affected): T029, T034.
- FR-015 (documentation): T039.

**User Stories**:
- US-1 (Fresh install без Sign-In): T031.
- US-2 (SOS работает локально всегда): T033, T021.
- US-3 (cloud-action activation → Sign-In flow → cloudAvailable=true): T032, T025.
- US-4 (cloud-augmented через LocalAlternative): T021, T033.

**Success Criteria**:
- SC-001 (main screen <3s, fresh install): T031.
- SC-002 (SOS <1s): T021, T033.
- SC-003 (0 cloud requests в local-mode): T031.
- SC-004 (persistence survives reboot): T032.
- SC-005 (Sign-In → flag <500ms): T017, T032.
- SC-006 (Sign-Out → flag <500ms): T017, T032.
- SC-007 (SignInExplanationScreen consistency): T022, T025.
- SC-008 (Xiaomi 11T regression): T035, T036, T041.
- SC-009 (Huawei без GMS): T031 (с DI override), `// TODO(physical-device)` T041.
- SC-010 (docs simple Russian): T039, T040.

**Constitution Gates** verified by:
- Rule 1 (domain isolation): T005-T009 в commonMain pure-Kotlin, T037 (verifyCloudIsolation).
- Rule 2 (ACL): T016 (DataStore wrap), T018 (TelephonyManager wrap), T020 (Intent wrap).
- Rule 4 (MVA): no CloudActionGate / CloudFeatureRegistry / CloudMode tasks — explicit.
- Rule 6 (mock-first): T010-T012 (fakes), T013-T015 (contract tests).
- Rule 7 (fitness functions): T037, T038.

---

## Open issues (для review перед /speckit.analyze)

1. **T026 DI framework**: spec assumes Koin, но реальный DI стек проекта нужно проверить — может быть Hilt / manual. Resolved at implementation start (T026).
2. **T023 string resources location**: KMP `composeResources/values/` или Android `app/src/main/res/values/`. Проверить existing pattern из TASK-1 (spec 015 wizard localization).
3. **T027 FCM token registration site**: точное местоположение в коде spec 019. Search task — должен быть `FirebaseMessaging.getInstance().token` + Firestore set call.

---

## Plain Russian summary (для не-разработчика)

Это **последовательность шагов** как мы будем строить TASK-49 (45 мелких задач).

**Шесть фаз** (примерно соответствуют 3-5 дням работы):

1. **Setup** (1 день начала): создать новый Gradle модуль `core/cloud`, добавить нужные библиотеки.
2. **Контракты** (несколько часов): описать 3 интерфейса (`CloudAvailability`, `LocalAlternative`, `EmergencyNumberResolver`) + data типы — это чистый Kotlin без Android.
3. **Fake-версии и контрактные тесты** (несколько часов): сделать «игрушечные» реализации для тестов + проверки что контракты соблюдаются.
4. **Реальные Android-реализации** (1 день): DataStore-implement + TelephonyManager-implement + SOS-dialer-implement + unit-tests.
5. **UI + DI + регрессия** (1 день): `SignInExplanationScreen` Composable, привязка через DI, регрессионный fix для TASK-5 FCM token.
6. **Проверки + документация + smoke** (1 день): инструментированные тесты на эмуляторе, проверка что spec 018 / 019 не сломались, документация на простом русском, smoke-check на physical Xiaomi 11T.

**После закрытия TASK-49**: TASK-6 (Root Key Recovery) возобновляется с готовым флагом `cloudAvailable` для своих решений.
