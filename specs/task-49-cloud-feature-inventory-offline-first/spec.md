# Feature Specification: TASK-49 — Cloud Availability Check + Local Alternatives (минимальная инфраструктура)

**Feature Branch**: `task-49-cloud-feature-inventory-offline-first`
**Created**: 2026-06-23
**Status**: Draft
**Input**: User description — «Архитектурное разделение приложения на local-only и cloud-required функционал. Без этой задачи приложение неявно требует Google Sign-In при первом запуске (FCM token регистрируется автоматически). Это нарушает device-self-sufficiency principle (decision 2026-06-15-deferred-cloud/01) и блокирует pure-local use case (Huawei без GMS, пользователи без Google-аккаунта). Также блокирует TASK-6: без явного определения "что считать первым cloud-action" TASK-6 не может правильно тригерить Setup screen для Recovery Backup. **Уточнение владельца 2026-06-23**: разделение делаем мягко — общая проверка + fallback для критических вещей. Не зашиваем жёсткий manifest, чтобы можно было через год сменить подход без переписывания всех S-задач.»

## Контекст и цель спека

**Где мы сейчас**: Phase 1 близка к завершению. TASK-1..5 реализованы. Spec 020 / TASK-6 был в работе, но во время `/speckit.clarify` обнаружилось: проект сейчас неявно cloud-first (FCM token регистрируется автоматически при запуске → нарушение device-self-sufficiency principle). TASK-6 ставится на Paused, ждёт TASK-49.

**Что строит TASK-49** (минимальный набор, owner mandate 2026-06-23 — Уровень B):
1. **`CloudAvailability` port** — общий сервис «есть ли сейчас cloud (GMS + сеть + Sign-In)». Используется любой фичей которая может это знать.
2. **`LocalAlternative` interface** — opt-in pattern для критических фич с локальным запасным путём. Не обязательный для всех cloud-фич.
3. **Один реальный example**: `SOSDialerAlternative` — SOS работает через локальный dialer (112 / 911 / 102) **всегда**, независимо от cloud-state.
4. **Регрессионный fix TASK-5**: FCM token регистрируется в Firestore **только после первого явного cloud-action**, не на app launch.
5. **Конвенция «первый cloud-action»** — короткое определение в документации + пример как использовать в коде.
6. **Документация** на простом русском — что есть, как использовать.

**Что НЕ строит TASK-49** (намеренно, для reversibility per CLAUDE.md rule 3):
- НЕТ central `CloudFeatureRegistry` manifest'а (был в первой версии спеки, убран по решению owner'а).
- НЕТ `CloudMode` enum принуждающего фичи к классификации.
- НЕТ inventory всех будущих фич — каждая S-задача сама решит свой подход при своём `/speckit.clarify`.
- НЕТ обязательного `LocalAlternative` для всех cloud-фич — только opt-in для критических.

**Архитектурное обещание** (фиксируется этой спекой): каждое устройство **самодостаточно**, cloud — это **upgrade**. Конкретный механизм разделения может эволюционировать; TASK-49 даёт минимальную инфраструктуру (CloudAvailability + LocalAlternative pattern) для текущего подхода.

## Про роли в этой задаче

Сценарий описан на примере **family-варианта** (`primary user` = бабушка, `remote administrator` = дочка-родственник). Это иллюстрация — в реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver`. Те же flow работают для других сегментов: clinic, B2B, self-care.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Fresh install, никакого Sign-In не требуется (Priority: P1)

`Primary user` устанавливает приложение. Открывает. **Никакого Google Sign-In экрана нет**. Сразу появляется wizard настройки: язык, разрешения (ROLE_HOME, POST_NOTIFICATIONS), тема, раскладка плиток. После wizard'а — главный экран с локальными плитками, локальными настройками, кнопкой SOS.

`Primary user` пользуется приложением хоть месяц без Sign-In и без интернета. Всё работает: главный экран, плитки локальных контактов, темы, размер шрифта, SOS.

**Why this priority**: фундаментальное обещание продукта. Без этого приложение нельзя использовать на Huawei, в странах с ограниченным Google, пользователями которые **не хотят** регистрироваться. Также блокирует TASK-6.

**Independent Test**: instrumentation test на `pixel_5_api_34` без подключения Google-аккаунта → fresh install → проверить (а) main screen рендерится без login prompt, (б) wizard проходится без Sign-In step, (в) после wizard главный экран функционален, (г) `inspect Firestore` показывает что FCM token этого устройства НЕ зарегистрирован.

**Acceptance Scenarios**:

1. **Given** свежее установленное приложение без Google-аккаунта на устройстве, **When** пользователь открывает приложение, **Then** появляется wizard настройки без Google Sign-In шага.
2. **Given** local-only режим активен, **When** телефон отключён от интернета, **Then** все local-features работают без задержек и без error-сообщений.
3. **Given** local-only режим, **When** packet capture снимает трафик 5 минут, **Then** нет запросов к `firestore.googleapis.com`, `firebase.googleapis.com`, `fcm.googleapis.com`, `googleapis.com/oauth2`.

---

### User Story 2 — SOS работает локально, всегда (Priority: P1)

`Primary user` тапает SOS. Через <1 секунду открывается стандартный Android dialer с предзаполненным emergency-номером (по locale устройства: 112 в Европе / 911 в США / 102 в России). Никаких задержек, никаких cloud-проверок, никаких login-prompts.

Если cloud активен + есть paired admin, **дополнительно** улетает push admin'у. Но это **bonus** — основной local path работает всегда.

**Why this priority**: SOS — критическая safety-функция. Любая задержка / падение / login-prompt = **product failure**. Должно работать всегда, независимо от cloud-state. Это **главный example** `LocalAlternative` pattern в этой спеке.

**Independent Test**: instrumentation test — fresh install (no Sign-In) → тап SOS → проверить (а) dialer открылся за <1 сек, (б) предзаполнен правильный emergency-номер по locale, (в) никаких cloud-запросов в этот момент.

**Acceptance Scenarios**:

1. **Given** свежее установленное приложение без Sign-In, **When** пользователь тапает SOS, **Then** dialer открывается за <1 секунду с emergency-номером по locale.
2. **Given** устройство в самолётном режиме, **When** SOS, **Then** dialer открывается мгновенно; интернет для SOS не нужен.
3. **Given** Huawei без GMS, **When** SOS, **Then** dialer открывается; никаких Google API не вызывается.

---

### User Story 3 — Активация cloud-фичи запускает Sign-In и Setup (Priority: P1)

`Primary user` решает использовать cloud-фичу — тапает «подключить родственника-помощника» в Settings. Появляется flow:

1. Промежуточный экран: «Эта функция требует подключения к облаку. Сначала войдите через Google, потом настройте пароль восстановления.» + кнопки «Продолжить» и «Отмена».
2. Google Sign-In flow.
3. После успешного Sign-In запускается **TASK-6** Setup screen (Recovery Backup passphrase).
4. После setup — flow возвращается к изначально запрошенной фиче (pairing с admin).

С этого момента cloud-фичи активны: FCM token регистрируется, sync включается.

**Why this priority**: trigger для всех cloud-фич и TASK-6. Без чёткого моста «нажал cloud-action → запустился setup» вся Phase 2 cloud-функциональность не работает. **Здесь TASK-49 предоставляет hook**: feature, которая хочет cloud, вызывает `CloudActionGate.requireCloud(callerId)` и получает либо `Available` (продолжает свой flow), либо `NeedsActivation` (UI показывает explanatory screen + delegates Sign-In/Setup, после успеха возвращает control в feature).

**Independent Test**: instrumentation test — fresh install без Sign-In → тап «подключить admin» (mock cloud-feature) → проверить (а) появляется explanatory screen, (б) после «Продолжить» запускается Sign-In, (в) после Sign-In запускается TASK-6 Setup (mocked), (г) после setup возвращается control в caller feature.

**Acceptance Scenarios**:

1. **Given** local-only режим, **When** feature вызывает `CloudActionGate.requireCloud("connectAdmin")`, **Then** показывается explanatory screen «нужен Google + пароль восстановления».
2. **Given** explanatory screen, **When** пользователь тапает «Продолжить», **Then** запускается Google Sign-In flow.
3. **Given** Sign-In успешен, **When** Sign-In завершается, **Then** автоматически запускается TASK-6 Setup screen (этот спек предоставляет hook для TASK-6, не реализует Setup сам).
4. **Given** TASK-6 Setup завершён, **When** flow продолжается, **Then** изначально запрошенный cloud-action выполняется (control возвращается в caller feature).
5. **Given** Sign-In уже сделан + setup уже сделан, **When** feature вызывает `requireCloud()`, **Then** `Available` возвращается мгновенно (без explanatory screen).
6. **Given** explanatory screen, **When** пользователь тапает «Отмена», **Then** caller feature получает `Cancelled` и обрабатывает gracefully.

---

### User Story 4 — Cloud-augmented фича через LocalAlternative (Priority: P1)

Feature «push admin'у на SOS» — пример cloud-augmented с обязательным local fallback. Когда cloud активен + есть paired admin:
1. SOS тапнут → dialer открыт (local path из User Story 2 — выполняется всегда).
2. **Дополнительно** в background улетает FCM push admin'у с координатой.

Когда cloud не активен:
- Только шаг 1 (dialer). Никакой попытки FCM, никаких error-сообщений в UI.

**Pattern**: каждая критическая фича, которая может работать в local mode, **opt-in реализует** `LocalAlternative` interface. Не критические фичи (например, photo upload) `LocalAlternative` не реализуют — они просто не делаются если cloud недоступен.

**Why this priority**: pattern для будущих critical-safety фич Phase 2 (Health Monitoring critical alerts, future hardware sensors). Без чёткого `LocalAlternative` interface каждая такая фича изобретает свой workaround.

**Independent Test**: unit test contract: фича реализует `LocalAlternative.executeLocally(context)`; `FakeCloudAvailability=Offline` → выполняется только local path; `Available` → local path выполняется + cloud path в parallel.

**Acceptance Scenarios**:

1. **Given** SOS feature реализует `LocalAlternative`, `CloudAvailability=Available`, paired admin, **When** SOS тапнут, **Then** dialer открывается **и** push улетает admin'у.
2. **Given** тот же feature, `CloudAvailability=Offline`, **When** SOS тапнут, **Then** только dialer; нет attempt к FCM, нет error-сообщения.
3. **Given** Huawei без GMS, **When** SOS, **Then** только dialer; UI не показывает cloud-related элементы.

---

### User Story 5 — CloudAvailability реагирует на network drop (Priority: P2)

`Primary user` активировал cloud-фичи. Едет в метро, теряется интернет на 10 минут. Через 30 секунд после потери связи:
- `CloudAvailability.state` переключается с `Available` на `Offline`.
- Features которые опрашивают state, могут адаптировать UI (например, плитки контактов без сетевых push'ей, но локально работают).
- SOS работает мгновенно (LocalAlternative).

Интернет возвращается → через 30 секунд state → `Available` → отложенные cloud-операции возобновляются.

**Why this priority**: realistic everyday scenario. Без реактивности UI «зависает» при потере связи.

**Independent Test**: JVM unit-tests state machine с simulated `NetworkCallback.onLost()` / `onAvailable()`; на эмуляторе toggle airplane mode и проверить state changes за <30 сек.

**Acceptance Scenarios**:

1. **Given** state=Available, **When** интернет пропал, **Then** state → Offline за <30 секунд.
2. **Given** state=Offline, **When** интернет вернулся, **Then** state → Available за <30 секунд.

---

### Edge Cases

- **GMS available, network OK, но Sign-In ещё не сделан**: `state = SignInRequired` (cloud доступен, но не активирован пользователем). Cloud-фичи могут проактивно показать «нужен Sign-In».
- **Sign-In есть, но Firebase Auth token expired**: state остаётся `Available`, cloud-операция fails при попытке → handled на уровне feature (refresh token). CloudAvailability не отслеживает token validity (out of scope).
- **Пользователь sign-out руками в Settings**: state → `SignInRequired` мгновенно. Locally cached data остаётся (для re-Sign-In).
- **Huawei с microG**: microG имитирует GMS. CloudAvailability должен conservatively treat как `Disabled` если `GoogleApiAvailability.SERVICE_SUCCESS` не получен. Это safer than false-positive `Available`.
- **CloudAvailability запрашивается из background WorkManager**: state читается из in-memory cache (StateFlow `.value`); если worker запускается до первого probe — state=`Unknown` → worker НЕ делает cloud-операцию, ждёт следующего invocation.
- **Очень медленная сеть (3G на 50 Кбит/с)**: state=Available (сеть есть), операции тайм-аутятся per feature. Не задача CloudAvailability.
- **Регрессионный fix TASK-5: existing users на Xiaomi 11T**, которые **уже** имеют зарегистрированный FCM token от старого поведения. Token остаётся, новый код не дерегистрирует. New installs пойдут по новому flow.
- **Что считать «явным cloud-action»**: explicit user tap → feature вызывает `CloudActionGate.requireCloud(callerId)`. НЕ background WorkManager job (он просто читает state и решает выполнять или skip), НЕ автоматический sync на app launch, НЕ FCM token registration on first install.

## Requirements *(mandatory)*

### Functional Requirements

**CloudAvailability port**:

- **FR-001**: `CloudAvailability` port MUST be declared в `core/cloud/commonMain` (KMP). Exposes `val state: StateFlow<CloudState>` и `suspend fun refresh()`.
- **FR-002**: `CloudState` sealed class MUST include states: `Unknown` (initial, до первого probe), `Available` (GMS + network + Sign-In OK), `SignInRequired` (GMS + network OK, no Sign-In), `Offline` (no network), `Disabled(reason: String)` (no GMS / no Firebase / user opt-out).
- **FR-003**: Android `CloudAvailabilityImpl` MUST observe `GoogleApiAvailability`, `ConnectivityManager` callbacks, `FirebaseAuth.AuthStateListener`. State updates MUST propagate в `StateFlow` automatically. Manual `refresh()` MUST re-check all three.
- **FR-004**: State transitions MUST be observable real-time в Compose UI через `collectAsState()`.
- **FR-005**: CloudAvailability MUST NOT persist state в DataStore. State живёт в memory only (StateFlow). При app restart — `state=Unknown` до первого probe (защита от stale state).

**LocalAlternative interface (opt-in pattern для критических фич)**:

- **FR-006**: `LocalAlternative` interface MUST provide `suspend fun executeLocally(context: ActionContext): ActionResult`. Реализуется фичей **только если** есть осмысленный локальный путь. Не обязательный для всех cloud-фич.
- **FR-007**: Реализация SOS LocalAlternative (`SOSDialerAlternative`) MUST open Android dialer (`Intent.ACTION_DIAL`) с emergency-номером по locale. **`ACTION_DIAL` always** (не `ACTION_CALL` — не требуем `CALL_PHONE` permission в этой спеке, пользователь тапает «вызов» в dialer вручную).
- **FR-008**: Emergency-номер MUST determined через `EmergencyNumberResolver` adapter (Android: `TelephonyManager.getCurrentEmergencyNumberList()` API 29+, fallback на hardcoded map для < API 29).

**CloudActionGate (мост между cloud-фичей и Sign-In/Setup)**:

- **FR-009**: `CloudActionGate` port в `core/cloud/commonMain` MUST provide `suspend fun requireCloud(callerId: String): CloudGateResult`. Возвращает: `Available` (можно продолжать), `NeedsActivation` (триггерит UI explanatory + Sign-In + TASK-6 Setup flow, потом возвращает `Available` или `Cancelled`), `Disabled(reason)` (cloud невозможен — например Huawei).
- **FR-010**: Android adapter `CloudActionGateImpl` MUST show explanatory screen «Эта функция требует подключения к облаку. Сначала войдите через Google, потом настройте пароль восстановления.» + кнопки «Продолжить» / «Отмена». После «Продолжить» — launch Sign-In flow через `AuthProvider` (TASK-3) → после успеха — delegate TASK-6 Setup → после setup — return `Available` в caller.
- **FR-011**: Если Sign-In уже сделан + Setup уже сделан (Recovery Backup vault существует) — `requireCloud()` возвращает `Available` мгновенно, без explanatory screen.
- **FR-012**: Если пользователь тапает «Отмена» — `requireCloud()` возвращает `Cancelled`. Caller feature обрабатывает gracefully (показывает «отменено» / возвращается на предыдущий screen).

**TASK-5 regression fix**:

- **FR-013**: FCM token registration в Firestore MUST be deferred до первого успешного `CloudActionGate.requireCloud()` → `Available`. До того момента token может быть **получен** от FCM library, но НЕ persisted в Firestore.
- **FR-014**: Existing users (installed APK с прежним behavior) NOT affected — их already-registered FCM tokens остаются. Dereg только при explicit account deletion (TASK-12).

**Convention «первый cloud-action»** (закрепляется в docs, не в коде):

- **FR-015**: Definition: «первый cloud-action» = explicit user tap → feature вызывает `CloudActionGate.requireCloud(callerId)`. **Не**: background WorkManager jobs, automatic syncs на app launch, FCM library internal initialization. Документация (FR-016) MUST содержать примеры.

**Documentation**:

- **FR-016**: `docs/dev/cloud-availability.md` MUST describe (на простом русском для не-разработчика):
  - Что такое `CloudAvailability` (общая проверка cloud).
  - Что такое `CloudActionGate` (мост для cloud-фичи к Sign-In/Setup).
  - Что такое `LocalAlternative` (opt-in pattern для critical features).
  - Что считать «первым cloud-action» (definition + примеры).
  - Как добавить новую cloud-фичу (короткий чек-лист).
  - **Явно**: «текущий подход = `CloudAvailability + LocalAlternative + CloudActionGate`. Через год может смениться (например, per-user opt-in flag для каждой фичи). Эта документация — snapshot текущего решения, не contract на навсегда».

### Key Entities

- **CloudAvailability** — port, центральный сервис проверки «есть ли cloud». Singleton в DI.
- **CloudState** — sealed class состояний (`Unknown` / `Available` / `SignInRequired` / `Offline` / `Disabled`).
- **CloudActionGate** — port, мост между cloud-фичей и flow «Sign-In → Setup → return».
- **CloudGateResult** — sealed class результата (`Available` / `NeedsActivation` / `Disabled` / `Cancelled`).
- **LocalAlternative** — opt-in interface для критических фич с локальным fallback.
- **ActionContext** — input для `LocalAlternative.executeLocally()` (callerId, parameters).
- **ActionResult** — output из `LocalAlternative.executeLocally()` (success / failure + message).
- **EmergencyNumberResolver** — adapter получения emergency-номера по locale.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Свежее установленное приложение запускается и показывает функциональный главный экран **без Google Sign-In** за <3 секунды на `pixel_5_api_34`.
- **SC-002 [backlog]**: SOS работает (открывает dialer с emergency-номером) за **<1 секунду** в любом cloud-state, включая `Disabled` / `Offline` / `Unknown`.
- **SC-003 [backlog]**: В local-only режиме (без Sign-In) packet capture за 5 минут показывает **0 запросов** к Firebase / Firestore / FCM endpoints.
- **SC-004**: `CloudAvailability.state` переключается с `Available` на `Offline` за **<30 секунд** после потери network (на эмуляторе через airplane mode toggle).
- **SC-005 [backlog]**: Тап на cloud-required feature на устройстве без Sign-In показывает explanatory screen за **<500ms**.
- **SC-006**: SOS LocalAlternative тестируется contract test'ом: `FakeCloudAvailability=Offline` + `FakeCloudAvailability=Available` — оба сценария проходят.
- **SC-007**: Existing users на physical device #1 (currently Xiaomi 11T) post-upgrade сохраняют FCM token (regression check: spec 018 round-trip + spec 019 FCM smoke passes).
- **SC-008 [backlog]**: Huawei без GMS (или DI-override `SERVICE_MISSING`) — приложение запускается, проходит wizard, использует local-features. Cloud-фичи показывают `CloudGateResult.Disabled(reason)` graceful. Никаких crashes.
- **SC-009 [backlog]**: Documentation `cloud-availability.md` написана на простом русском, читается non-developer владельцем за <10 минут.

## Assumptions

- Spec 016 (F-CRYPTO), 017 (F-4 AuthProvider), 018 (F-5b), 019 (F-5c) уже merged — TASK-49 их не модифицирует кроме regression fix TASK-5 (FR-013).
- TASK-6 (Root Key + Recovery Backup) находится в Paused state. После TASK-49 closure — TASK-6 возобновляется с готовым `CloudActionGate` (через который реализуется FR-008/009/010 TASK-6 spec'и).
- `GoogleApiAvailability` API стабильно работает на minSdk проекта (API 26+).
- `ConnectivityManager.NetworkCallback` API стабильно работает на minSdk (API 24+).
- `FirebaseAuth.addAuthStateListener` доступен (existing usage в TASK-3).
- `TelephonyManager.getCurrentEmergencyNumberList()` доступен на API 29+ (с fallback hardcoded map для нижних API).
- DI стек — Koin (или подтвердить через research при `/speckit.plan`).

## Local Test Path *(mandatory)*

- **Emulator / device**:
  - JVM unit tests для CloudAvailability state machine + CloudActionGate contract + LocalAlternative contract + EmergencyNumberResolver fallback logic.
  - `pixel_5_api_34` emulator via skill `android-emulator` — install → main screen без Sign-In → local features работают.
  - `pixel_5_api_34` emulator — тап cloud-action (mock feature) → explanatory screen → mocked Sign-In flow.
  - physical device #1 (currently Xiaomi 11T) — regression FCM token timing + spec 018 ciphertext round-trip + spec 019 FCM smoke.
  - Emulator без GMS (DI-override `GoogleApiAvailability` → SERVICE_MISSING) — Huawei-mode test.
- **Fake adapters used**:
  - `FakeCloudAvailability` (controllable state) — для тестов features.
  - `FakeCloudActionGate` (returns predetermined CloudGateResult) — для тестов caller-features.
  - `FakeLocalAlternative` (deterministic result) — для тестов pattern.
  - `FakeEmergencyNumberResolver` (returns fixed number) — для unit-tests SOS dialer.
  - `FakeAuthIdentity` — переиспользуется из TASK-3.
- **Fixtures / seed data**: нет (нет wire-format'а).
- **Verification commands**:
  - `./gradlew :core:cloud:jvmTest --tests "*CloudAvailability*"` — state machine.
  - `./gradlew :core:cloud:jvmTest --tests "*CloudActionGate*"` — contract.
  - `./gradlew :core:cloud:jvmTest --tests "*LocalAlternative*"` — contract.
  - `./gradlew :core:cloud:jvmTest --tests "*EmergencyNumberResolver*"` — fallback logic.
  - `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="OfflineFirstE2ETest"` — fresh install no Sign-In.
  - `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="SOSLocalAlternativeE2ETest"` — SOS dialer в любом state.
  - `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="CloudActionGateE2ETest"` — tap → explanatory → Sign-In flow.
  - `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="CloudConfigEncryptionE2ETest"` — spec 018 regression.
- **Cannot-test-locally gaps**:
  - Regression test на **physical device #1 (currently Xiaomi 11T)** — FCM token timing (FR-013) + spec 018/019 regression. `// TODO(physical-device): pre-merge run regression suite on Xiaomi 11T`.
  - Real Huawei device — emulator override покрывает большинство, но real device verification желательна. `// TODO(physical-device): future Huawei verification when device available`.

## AI Affordance *(mandatory)*

**no AI affordance — internal architectural infrastructure**.

`CloudAvailability`, `CloudActionGate`, `LocalAlternative` — internal infrastructure для разделения local/cloud работы. AI-agent не должен иметь возможности менять состояние CloudAvailability или вызывать `LocalAlternative.executeLocally()` напрямую — это разрушит инвариант продукта (SOS должен идти только через user-initiated tap, не через AI).

Если в будущем AI-agent захочет инспектировать «доступна ли cloud-фича X» — это **отдельная capability через TASK-33 F-2** (Capability Registry в Phase 4) с явными gates. TASK-49 не открывает этого affordance.

## OEM Matrix *(mandatory if feature touches device behavior)*

Feature touches: `GoogleApiAvailability` (GMS detection), `ConnectivityManager` (network state), `FirebaseAuth` state observation, `TelephonyManager.getCurrentEmergencyNumberList()`, `Intent.ACTION_DIAL`.

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|-------------------------|---------------------|
| Stock Android (Pixel) | baseline | — | emulator `pixel_5_api_34` |
| Samsung One UI | `ConnectivityManager` callbacks работают стандартно; GMS всегда присутствует на consumer-устройствах | — | `// TODO(physical-device): Samsung One UI verification when device available` |
| Xiaomi MIUI | Background restrict может мешать `NetworkCallback` propagation в background; `GoogleApiAvailability` работает стандартно | StateFlow в memory; на background callback может быть delayed — приемлемо (state не realtime-critical) | physical device #1 (currently Xiaomi 11T) regression test |
| Huawei EMUI | **No GMS** → `GoogleApiAvailability.SERVICE_MISSING`. `CloudAvailability.state` навсегда `Disabled(reason="GMS unavailable")`. | Per FR-002: Disabled — explicit state; cloud-фичи получают `CloudGateResult.Disabled` graceful; local features работают полностью | `// TODO(physical-device): real Huawei verification when device available; emulator test через DI override` |
| microG (Lineage / Calyx) | microG имитирует GMS. Может возвращать `SERVICE_VERSION_UPDATE_REQUIRED` или `SERVICE_INVALID` | Conservative: treat как `Disabled` если SERVICE_SUCCESS не получен | `// TODO(future-spec): CalyxOS / LineageOS verification — out of MVP scope` |

## Exit Ramps and Design Reversibility

Этот раздел фиксирует **два-way door classification** этой спеки per CLAUDE.md rule 3, по явному мандату owner'а 2026-06-23.

**Это TWO-WAY DOOR**, не one-way. Текущий подход (`CloudAvailability` + `LocalAlternative` opt-in + `CloudActionGate`) — **один из возможных**. Конкретные exit ramps:

1. **Сменить «classification by feature mode» на «classification by data sensitivity»**:
   - Future trigger: requirement «sensitive data → cloud-only, non-sensitive → может быть local cache».
   - Cost of switch: ~1-2 weeks. `CloudAvailability` port **остаётся** (нейтральный). Удаляются `CloudActionGate` calls в фичах, заменяются на per-data-type policy check. `LocalAlternative` interface остаётся для критических фич.
   - Что НЕ ломается: `CloudAvailability` (нейтральный сервис), документация (обновляется), все S-задачи Phase 2 которые сами реализовывали свой policy.

2. **Сменить на «per-user opt-in flag для каждой фичи»**:
   - Future trigger: user requests «я хочу самому решать sync ли контакты в облако, независимо от feature mode».
   - Cost of switch: ~1 week. Добавляется UserCloudPreferences port; `CloudActionGate` дополнительно проверяет user preference перед triggering Sign-In flow. `LocalAlternative` interface остаётся.
   - Что НЕ ломается: всё кроме UI Settings (где добавляются новые toggles).

3. **Сменить на «hybrid approach» (sensitive data per-feature + non-sensitive per-user-opt-in)**:
   - Composite из (1) и (2). Cost ~2-3 weeks. Те же exit ramps работают независимо.

4. **Полностью убрать TASK-49 architecture, перейти на «each feature self-determines»**:
   - Future trigger: TASK-49 оказался over-engineered (3+ S-задач реализовали свой policy без использования `CloudActionGate`).
   - Cost: ~1 week. Удаление `CloudActionGate`. `CloudAvailability` **остаётся** (нейтральный сервис, useful in any architecture). `LocalAlternative` interface — оставить как convention или удалить.

**Что навсегда полезно** (даже при любом redesign):
- `CloudAvailability` port — нейтральная факт-проверка «есть ли cloud».
- `EmergencyNumberResolver` — emergency-номера всегда полезны.
- TASK-5 regression fix (FCM token timing) — это **bug fix**, не архитектурное решение, навсегда правильное.

**Что специфично для текущего подхода и может смениться**:
- `CloudActionGate` (мост к Sign-In/Setup).
- `LocalAlternative` interface (opt-in).
- Convention «первый cloud-action» definition.

**Документация** (FR-016) MUST явно указать: «эта architecture — текущий snapshot. Через 6-12 месяцев может смениться без переписывания CloudAvailability core».

## Dependencies

**Hard dependencies** (должны быть merged до TASK-49):

- **TASK-3 (F-4 AuthProvider)** ✅ — для Firebase Auth state observation в `CloudAvailabilityImpl` + Sign-In flow в `CloudActionGateImpl`.
- **TASK-2 (F-CRYPTO)** ✅ — нужен для будущего использования через TASK-6.

**Soft dependencies** (informational):

- TASK-6 — **зависит от** TASK-49 (currently Paused, ждёт нас; будет использовать `CloudActionGate`).
- TASK-5 (F-5c FCM push) ✅ — будет **изменён** этой спекой (regression fix FR-013).
- decision 2026-06-15-deferred-cloud/01 — реализуется этой спекой.
- TASK-33 (F-2 Capability Registry, Phase 4) — будущий potential consumer `CloudAvailability` через ExposureAdapter (вне scope сейчас).

## Out of Scope

- **`CloudFeatureRegistry` central manifest** — намеренно НЕ строим (owner mandate 2026-06-23 — Уровень B). Каждая S-задача сама решит свой подход при своём `/speckit.clarify`.
- **`CloudMode` enum принуждающий фичи к классификации** — НЕ строим.
- **Inventory всех будущих фич** — НЕ строим. Только SOS как example.
- **`LocalAlternative` для всех cloud-фич** — НЕ обязательный. Только opt-in для критических.
- iOS `CloudAvailability` adapter — Phase 4 (V-1 / TASK-26).
- Subscription billing gates — TASK-15 S-10 отдельная задача.
- Прозрачное переключение online/offline в realtime UI — post-MVP.
- Network quality detection (3G vs 5G vs WiFi) — out of scope.
- VPN / proxy detection — out of scope.
- Token validity check (refresh, expiry) — per-feature concern.
- DataStore persist `CloudState` — НЕ делаем (FR-005 explicit).
- `ACTION_CALL` для SOS auto-dial — НЕ делаем (требует `CALL_PHONE` permission, увеличивает onboarding friction). User тапает «вызов» в dialer вручную.
- Real-time push к features при state change — features сами `collectAsState()` если хотят.

## Constitution Gates (предварительно — финализируются в `/speckit.plan`)

- **Rule 1** (domain isolated): `CloudAvailability`, `CloudActionGate`, `LocalAlternative`, `CloudState`, `CloudGateResult`, `ActionContext`, `ActionResult` — pure domain в `core/cloud/commonMain`; никаких Android / Firebase / GoogleApiAvailability типов в signatures.
- **Rule 2** (ACL): `GoogleApiAvailability`, `ConnectivityManager`, `FirebaseAuth`, `TelephonyManager`, `Intent` — wrap'нуты внутри Android adapters (`CloudAvailabilityImpl`, `CloudActionGateImpl`, `EmergencyNumberResolverImpl`); их типы не вытекают в core.
- **Rule 3** (one-way doors): **TASK-49 = two-way door**. Exit ramps зафиксированы в разделе `Exit Ramps and Design Reversibility`. Стоимость смены подхода — 1-2 weeks без переписывания `CloudAvailability` core.
- **Rule 4** (MVA): минимальная инфраструктура. НЕ строим `CloudFeatureRegistry` / `CloudMode` enum (были в первой версии, убраны после Уровень B mandate). `CloudActionGate` — необходимый мост к TASK-6 + Sign-In flow. `LocalAlternative` — необходимый interface для SOS критической фичи.
- **Rule 5** (wire-format): n/a — TASK-49 не вводит wire-format'ов.
- **Rule 6** (mock-first): `FakeCloudAvailability`, `FakeCloudActionGate`, `FakeLocalAlternative`, `FakeEmergencyNumberResolver` — обязательны для всех unit-tests cloud-aware фич.
- **Rule 7** (fitness functions): `verifyCloudIsolation` Gradle task: `:core:cloud` depends только на Kotlin stdlib + coroutines. Никаких Android / Firebase deps в `commonMain`.
- **Rule 8** (server-roadmap): `CloudAvailability.Disabled(reason="own server unreachable")` будет добавлен когда переедем на own server (SRV-* в server-roadmap.md). Inline TODO в `CloudAvailabilityImpl`.

## References

- [decision 2026-06-15-deferred-cloud/01](../../docs/product/decisions/2026-06-15-deferred-cloud/01-deferred-sign-in.md) — primary source принципа.
- [CLAUDE.md](../../CLAUDE.md) — rules 1, 2, 3 (two-way door), 4, 7.
- [checklist-device-self-sufficiency](../../.claude/skills/checklist-device-self-sufficiency/SKILL.md) — validates compliance.
- [backlog TASK-49](../../backlog/tasks/task-49%20-%20Cloud-Feature-Inventory-Offline-First-Architecture.md) — portfolio entry.
- [backlog TASK-6 Paused](../../backlog/tasks/task-6%20-%20F-5-Root-Key-Hierarchy-Owner-Recovery.md) — заблокирована этой спекой.
- [backlog TASK-5 F-5c](../../backlog/tasks/task-5%20-%20F-5c-FCM-config-updated-push-trigger.md) — будет regression-fixed.
- [docs/dev/server-roadmap.md](../../docs/dev/server-roadmap.md) — exit ramp для CloudAvailability adapter.
- Memory: `project_deferred_cloud_architecture`, `project_spec_017_018_019_e2e_working`.

---

## Plain Russian summary (для не-разработчика владельца)

**Что закрывает TASK-49 (минимальная версия по твоему мандату 2026-06-23)**: даёт **минимальную инфраструктуру** для разделения приложения на local-only и cloud-required, **без жёсткого зашитого манифеста** который потом нельзя сменить. Если через год придёт идея «делить по-другому» — это будет возможно за 1-2 недели, без переписывания всех S-задач.

**Что строим**:

1. **Один сервис `CloudAvailability`** — общая проверка «есть ли сейчас облако» (интернет + Google-сервисы + Sign-In). Любая фича может его спросить. Это **нейтральная** вещь, никуда не денется при смене подхода.

2. **Интерфейс `LocalAlternative`** — opt-in pattern для **критических** фич с локальным fallback. Не обязательный для всех. Пример: SOS = всегда открывает обычный dialer с номером 112/911/102, а дополнительно отправляет push родственнику если есть облако.

3. **Мост `CloudActionGate`** — если фича хочет cloud-операцию, она вызывает `requireCloud("connectAdmin")`. Если cloud есть → возвращает «можно работать». Если нет → показывает экран «нужен Google + пароль восстановления → Продолжить / Отмена», запускает Sign-In, потом TASK-6 Setup, потом возвращает control в фичу.

4. **Регрессионный fix** существующего TASK-5: FCM token (для push-уведомлений) сейчас регистрируется при первом запуске → меняем на «только после первого явного cloud-действия пользователя».

5. **Документация** на простом русском — что есть, как использовать, и **явно** что текущий подход может смениться без переписывания core.

**Что НЕ делаем сейчас** (намеренно — для reversibility):

- НЕ строим central `CloudFeatureRegistry` manifest (был в первой версии спеки, убран после твоего mandate).
- НЕ строим `CloudMode` enum принуждающий фичи к классификации.
- НЕ делаем inventory всех будущих фич.
- НЕ обязываем все cloud-фичи реализовать `LocalAlternative` — только критические.

**Что станет возможным после TASK-49**: TASK-6 расPaused'ится с готовым `CloudActionGate` для FR-008 «когда показать Setup screen». Каждая S-задача Phase 2 сама решит свой local/cloud split при своём clarify (не привязана к жёсткой классификации).

**Главное архитектурное обещание** (фиксируется навсегда): каждое устройство **самодостаточно**, cloud — это **upgrade**. Конкретный механизм разделения может эволюционировать.
