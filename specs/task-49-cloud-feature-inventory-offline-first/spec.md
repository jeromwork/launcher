# Feature Specification: TASK-49 — Cloud Availability Boolean + SignIn Explanation + SOS Local Alternative

**Feature Branch**: `task-49-cloud-feature-inventory-offline-first`
**Created**: 2026-06-23
**Status**: Clarified
**Input**: User description — «Разделение приложения на local-only и cloud-режимы. Один булев в файле, переключается на двух событиях (Sign-In / Sign-Out) через AuthProvider oповещалку. Никаких других проверок (network, GMS, token expiry, refresh) — это не задача TASK-49.»

## Контекст и цель спека

**Где мы сейчас**: Phase 1. TASK-1..5 реализованы. TASK-6 (Recovery Backup) на Paused, ждёт TASK-49. Проблема: приложение неявно cloud-first (FCM token регистрируется автоматически), нарушает device-self-sufficiency principle.

**Что строит TASK-49 (минимум)**:

1. **`cloudAvailable` булев флаг** в файле на диске (DataStore). Переключается **только** на двух событиях:
   - Sign-In → `true`.
   - Sign-Out → `false`.
2. **Подписка на `AuthProvider`** (TASK-3) через push-oповещалку. Никакого polling, никаких проверок network / GMS / token validity.
3. **`SignInExplanationScreen`** — единое explanatory Composable, переиспользуется wizard'ом (TASK-7) и Settings cloud-actions. Краткое объяснение «зачем войти + что станет доступно» + кнопки «Войти через Google» / «Отмена».
4. **`LocalAlternative` interface** — opt-in pattern для критических фич с локальным fallback. Реализация: `SOSDialerAlternative` — SOS всегда открывает Android dialer с emergency-номером, независимо от `cloudAvailable`.
5. **Regression fix TASK-5**: FCM token регистрируется в Firestore **только при `cloudAvailable = true`**.
6. **Документация `docs/dev/cloud-availability.md`** на простом русском — как работает флаг, как cloud-фичи его читают, что считать "первым cloud-action".

**Что TASK-49 НЕ делает** (намеренно, per CLAUDE.md rule 4 MVA):

- НЕ проверяет network state (отдельная concern других фич).
- НЕ проверяет GMS availability (отдельная concern, если потребуется).
- НЕ проверяет token expiry / refresh (Firebase SDK сам).
- НЕ делает probes / опросов внешних сервисов.
- НЕ реагирует на airplane mode / WiFi disconnect.
- НЕ пересчитывает state при app launch (читает persist файл).
- НЕ знает про Setup / backup / TASK-6.
- НЕ имеет CloudActionGate port (cloud-фичи сами читают `cloudAvailable`).
- НЕ имеет central CloudFeatureRegistry manifest.

**Архитектурное обещание**: каждое устройство **самодостаточно**, cloud — это **upgrade**. Конкретный механизм разделения minimal и reversible.

## Про роли в этой задаче

Сценарий описан на примере **family-варианта** (`primary user` = бабушка, `remote administrator` = дочка-родственник). Это иллюстрация — в реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver`. Те же flow работают для других сегментов: clinic, B2B, self-care.

## Clarifications

### 2026-06-23 — Pre-plan clarification pass

| # | Вопрос | Резолюция |
|---|---|---|
| 1 | Как `CloudAvailability` определяет «Setup сделан»? | **Никак.** TASK-49 не знает про Setup / backup / TASK-6 internals. TASK-49 знает только про Sign-In/Sign-Out через `AuthProvider` oповещалку. Что делать после Sign-In (показать ли Setup screen) — concern TASK-6, не TASK-49. |
| 2 | Как обрабатывается Sign-Out? | **Тем же `AuthProvider.identityFlow`** push-механизмом, что и Sign-In. Один Flow в обе стороны (Sign-In → `true`, Sign-Out → `false`). Никакого отдельного sign-out flow. |
| 3 | Что при network drop / GMS missing / token expired? | **Не задача TASK-49.** Эти concerns обрабатываются: (а) каждой cloud-фичей самостоятельно через try/catch на 401, (б) Firebase SDK для token refresh, (в) другими специками если потребуется network monitoring. TASK-49 не проверяет ничего кроме Sign-In state. |
| 4 | Где живёт state — runtime memory или persist файл? | **Persist в DataStore.** Булев читается при app launch без пересчёта. Меняется только на Sign-In / Sign-Out события. Если возникнет рассинхрон с Firebase token state — это не задача TASK-49 (обрабатывается через refresh механизм других тасок). |
| 5 | Нужен ли `CloudActionGate` port как абстракция-обёртка? | **Нет.** Cloud-фичи читают `cloudAvailable` напрямую (булев в DataStore + reactive Flow для подписчиков). Дополнительная абстракция нарушает CLAUDE.md rule 4 (MVA) — single-implementation interface "на будущее". Если 3+ фичи дублируют один и тот же pattern — extract потом, не сейчас. |
| 6 | Что показывать пользователю перед Sign-In flow? | **Единое explanatory screen** (`SignInExplanationScreen`) с кратким объяснением «зачем войти + что станет доступно» (3-4 generic пункта на простом русском) + кнопки «Войти через Google» / «Отмена». Используется одинаково в wizard (TASK-7) и в Settings cloud-actions. Все строки через string resources (i18n via TASK-1 infrastructure). |
| 7 | Direction of dependency между TASK-49 и TASK-3 AuthProvider? | **TASK-49 → TASK-3** (правильно). `AuthProvider` публикует state через `identityFlow: StateFlow<AuthIdentity?>` (push-стиль). `CloudAvailability` подписан. `AuthProvider` не знает кто его слушает — pure Observer pattern. |
| 8 | TASK-5 FCM token при UID switching? | Sign-Out (UID1) → `cloudAvailable = false` → FCM не активен. Sign-In (UID2) → `cloudAvailable = true` → новый token регистрируется для UID2. Orphan token UID1 в Firestore — concern TASK-12 (Account Deletion) или server cleanup, не TASK-49. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Fresh install без Sign-In (Priority: P1)

`Primary user` устанавливает приложение. Открывает. Никакого Google Sign-In экрана нет. Wizard настройки: язык, разрешения, тема, раскладка плиток. После wizard'а — главный экран с локальными плитками, SOS-кнопкой.

`Primary user` пользуется приложением месяцами без Sign-In и без интернета. Всё работает локально.

**Why this priority**: фундаментальное обещание продукта. Без этого приложение не работает на Huawei, в регионах без Google, для пользователей без Google-аккаунта.

**Independent Test**: instrumentation test на `pixel_5_api_34` без подключения Google-аккаунта → fresh install → проверить (а) main screen без login prompt, (б) wizard проходится без Sign-In step, (в) `cloudAvailable=false` в DataStore, (г) FCM token НЕ зарегистрирован в Firestore.

**Acceptance Scenarios**:

1. **Given** свежая установка без Google-аккаунта, **When** пользователь открывает приложение, **Then** wizard без Sign-In шага, после wizard'а главный экран функционален, `cloudAvailable=false`.
2. **Given** `cloudAvailable=false`, **When** packet capture снимает трафик 5 минут, **Then** нет запросов к Firebase / FCM endpoints.

---

### User Story 2 — SOS работает локально всегда (Priority: P1)

`Primary user` тапает SOS. Через <1 секунду открывается Android dialer с emergency-номером (112 / 911 / 102 по locale). Работает в любом cloud-state.

**Why this priority**: SOS — критическая safety-функция. Любая задержка = product failure. Это **главный example** `LocalAlternative` pattern в этой спеке.

**Independent Test**: instrumentation test — fresh install (no Sign-In) → тап SOS → проверить dialer открылся за <1 сек с emergency-номером по locale.

**Acceptance Scenarios**:

1. **Given** `cloudAvailable=false`, **When** SOS тапнут, **Then** dialer открывается за <1 секунду с emergency-номером по locale.
2. **Given** airplane mode (нет сети), **When** SOS, **Then** dialer открывается мгновенно.
3. **Given** Huawei без GMS, **When** SOS, **Then** dialer работает; никаких Google API вызовов.

---

### User Story 3 — Активация cloud-фичи показывает explanation + Sign-In (Priority: P1)

`Primary user` решает использовать cloud-фичу — тапает «подключить родственника» в Settings. Видит `SignInExplanationScreen`:
- Заголовок «Войдите через Google, чтобы получить дополнительные возможности».
- 3-4 пункта что станет доступно (резервная копия, удалённая настройка, синхронизация).
- Кнопки «Войти через Google» / «Отмена».

Тапает «Войти» → Google Sign-In flow → после успеха → `cloudAvailable = true` → flow возвращается к исходному действию (подключение admin).

**Why this priority**: trigger всех cloud-фич Phase 2. Также используется в wizard'е как опциональный шаг.

**Independent Test**: instrumentation test — тап cloud-action (mock feature) → проверить (а) появляется `SignInExplanationScreen`, (б) после «Войти» запускается Sign-In flow, (в) после успеха `cloudAvailable=true` в DataStore, (г) caller-feature получает control обратно.

**Acceptance Scenarios**:

1. **Given** `cloudAvailable=false`, **When** cloud-фича тригерит Sign-In requirement, **Then** показывается `SignInExplanationScreen`.
2. **Given** explanatory screen, **When** «Войти через Google», **Then** запускается Google Sign-In flow.
3. **Given** Sign-In успешен, **When** flow завершается, **Then** `cloudAvailable = true` в DataStore, `AuthProvider.identityFlow` emit'нул identity, control возвращается в caller.
4. **Given** explanatory screen, **When** «Отмена», **Then** flow отменяется, `cloudAvailable` не меняется.
5. **Given** `cloudAvailable=true` (уже Sign-In), **When** cloud-фича стартует, **Then** работает без explanatory screen.

---

### User Story 4 — Cloud-augmented фича через LocalAlternative (Priority: P1)

Future feature «push admin'у на SOS» реализует `LocalAlternative`. Когда `cloudAvailable=true` + paired admin:
1. SOS тапнут → dialer открыт (local path — всегда выполняется).
2. **Дополнительно** background улетает FCM push admin'у.

Когда `cloudAvailable=false`:
- Только шаг 1 (dialer). FCM не пытается, errors не показывает.

**Why this priority**: pattern для будущих critical-safety фич с опциональным cloud enhancement.

**Independent Test**: unit test contract: feature реализует `LocalAlternative.executeLocally()`; FakeCloudAvailability=false → только local; true → local + cloud в parallel.

**Acceptance Scenarios**:

1. **Given** SOS реализует `LocalAlternative`, `cloudAvailable=true`, paired admin, **When** SOS тапнут, **Then** dialer открывается + push улетает admin'у.
2. **Given** `cloudAvailable=false`, **When** SOS, **Then** только dialer; нет attempt к FCM, нет error в UI.

---

### Edge Cases

- **AuthProvider emit'ит identity=null** (sign-out) → `cloudAvailable = false` записан в DataStore через push-обработчик. Без promotion / persistence для cloud-фич — они получают `false` через подписку на reactive flag (см. FR-002).
- **AuthProvider emit'ит identity (sign-in success)** → `cloudAvailable = true` записан в DataStore через push-обработчик.
- **App launch с `cloudAvailable=true` в DataStore** → флаг сразу читается, cloud-фичи могут работать. Никакого probe / network check.
- **App launch с `cloudAvailable=false`** → cloud-фичи в local mode по умолчанию.
- **TASK-5 FCM token при `cloudAvailable=false`** → token может быть получен от FCM library, но НЕ persisted в Firestore.
- **TASK-5 FCM token при `cloudAvailable=true → false` (sign-out)** → existing token остаётся в Firestore (cleanup — concern TASK-12 Account Deletion), новые tokens не пишутся.
- **Existing users post-upgrade с уже зарегистрированным FCM token** → token остаётся; новый код не дерегистрирует.

## Requirements *(mandatory)*

### Functional Requirements

**Persistent cloud flag**:

- **FR-001**: `CloudAvailability` port в `core/cloud/commonMain` MUST expose:
  - `suspend fun isCloudAvailable(): Boolean` — синхронное чтение текущего флага.
  - `val isCloudAvailableFlow: Flow<Boolean>` — reactive подписка для UI и других фич.
- **FR-002**: Implementation MUST persist флаг в DataStore (Android `Preferences DataStore` или multiplatform equivalent). Чтение при app launch — БЕЗ пересчёта из сторонних источников.
- **FR-003**: `CloudAvailabilityImpl` MUST subscribe to `AuthProvider.identityFlow` (TASK-3) при инициализации. На каждом emit:
  - `identity != null` → set flag = `true` в DataStore.
  - `identity == null` → set flag = `false` в DataStore.
- **FR-004**: NO other observers (NO `ConnectivityManager`, NO `GoogleApiAvailability`, NO token expiry check). Только `AuthProvider`.

**SignInExplanationScreen**:

- **FR-005**: Reusable Composable `SignInExplanationScreen` в `app/.../onboarding/` (new folder). Параметры: `onSignInClicked: () -> Unit`, `onCancelClicked: () -> Unit`.
- **FR-006**: Screen content MUST include:
  - Заголовок (через string resource).
  - 3-4 generic пункта что станет доступно (через string resources).
  - Кнопка «Войти через Google» (primary).
  - Кнопка «Отмена» (secondary).
- **FR-007**: Все строки экрана MUST через string resources (i18n compatible). Locales управляются через TASK-1 infrastructure (`procedure-translate-spec-strings`).
- **FR-008**: Screen MUST быть переиспользуемым:
  - Из wizard'а (TASK-7) как опциональный шаг.
  - Из Settings cloud-actions.
  - Из любой будущей cloud-фичи.

**LocalAlternative interface**:

- **FR-009**: `LocalAlternative` interface в `core/cloud/commonMain` MUST provide `suspend fun executeLocally(context: ActionContext): ActionResult`. Opt-in pattern — реализуется только критическими фичами.
- **FR-010**: `SOSDialerAlternative` MUST implement `LocalAlternative` для SOS feature. `executeLocally()` opens Android dialer (`Intent.ACTION_DIAL`) с emergency-номером по locale.
- **FR-011**: Emergency-номер MUST determined через `EmergencyNumberResolver` adapter:
  - Android API 29+: `TelephonyManager.getCurrentEmergencyNumberList()`.
  - Fallback: hardcoded map по `Locale` country code (RU=102, US=911, EU=112, etc.).
- **FR-012**: `SOSDialerAlternative.executeLocally()` MUST НЕ блокироваться на любой cloud-проверке; opens dialer мгновенно (<1 секунду).

**TASK-5 regression fix**:

- **FR-013**: FCM token registration в Firestore MUST проверять `CloudAvailability.isCloudAvailable() == true` перед persist. Если `false` — token может быть получен от FCM library, но не записывается в Firestore.
- **FR-014**: Existing users с уже-registered tokens NOT affected. Cleanup orphan tokens — concern TASK-12, не здесь.

**Documentation**:

- **FR-015**: `docs/dev/cloud-availability.md` MUST describe (на простом русском для не-разработчика):
  - Что такое `cloudAvailable` булев флаг (Sign-In tracker).
  - Как меняется (только через `AuthProvider` events).
  - Что считать «первым cloud-action» (explicit user tap → feature вызывает Sign-In flow).
  - Как cloud-фича читает флаг (через `isCloudAvailable()` или `isCloudAvailableFlow`).
  - Convention: «во время Sign-In flow конкурентные cloud-операции должны ждать или быть disabled» (рекомендация для consumers, не enforced в TASK-49).
  - Что TASK-49 НЕ делает (network, GMS, token expiry — это другие concerns).
  - **Явно**: «эта architecture — текущий snapshot. Через 6-12 месяцев может смениться без переписывания core».

### Key Entities

- **`cloudAvailable`** — булев флаг в DataStore. True = Sign-In есть; False = нет.
- **`CloudAvailability`** — port для чтения флага (sync + reactive Flow).
- **`LocalAlternative`** — opt-in interface для критических фич с локальным fallback.
- **`SOSDialerAlternative`** — implementation `LocalAlternative` для SOS (Android dialer + emergency number).
- **`EmergencyNumberResolver`** — adapter получения emergency-номера по locale.
- **`SignInExplanationScreen`** — reusable Composable с explanation + кнопки.
- **`ActionContext`** / **`ActionResult`** — input/output для `LocalAlternative.executeLocally()`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Свежее установленное приложение запускается и показывает функциональный главный экран **без Google Sign-In** за <3 секунды на `pixel_5_api_34`.
- **SC-002 [backlog]**: SOS открывает dialer с emergency-номером за **<1 секунду** независимо от `cloudAvailable` state.
- **SC-003 [backlog]**: В local-mode (`cloudAvailable=false`) packet capture 5 минут показывает **0 запросов** к Firebase / Firestore / FCM endpoints.
- **SC-004**: `cloudAvailable` булев persists в DataStore — переживает app kill / device reboot. После reboot читается без пересчёта.
- **SC-005 [backlog]**: Sign-In success → `cloudAvailable=true` в DataStore за <500ms (push через AuthProvider).
- **SC-006 [backlog]**: Sign-Out → `cloudAvailable=false` за <500ms (push через AuthProvider).
- **SC-007 [backlog]**: `SignInExplanationScreen` показывается одинаково из wizard и Settings (визуальное соответствие).
- **SC-008**: Existing users на physical device #1 (currently Xiaomi 11T) post-upgrade сохраняют FCM token (regression check: spec 018 round-trip + spec 019 FCM smoke passes).
- **SC-009 [backlog]**: Huawei без GMS — приложение запускается, проходит wizard, локальные features работают. `cloudAvailable` остаётся `false` навсегда (нет AuthProvider успешного Sign-In). Никаких crashes.
- **SC-010 [backlog]**: Documentation `cloud-availability.md` читается non-developer владельцем за <10 минут.

## Assumptions

- TASK-3 (F-4 AuthProvider) MUST expose `identityFlow: StateFlow<AuthIdentity?>` (reactive Flow). Если только suspend `getCurrentIdentity()` — потребуется micro-доработка TASK-3 как pre-requisite. Это **research для `/speckit.plan` фазы**.
- DataStore доступен в проекте (existing dependency).
- TASK-1 string resources infrastructure работает (для FR-007).
- `TelephonyManager.getCurrentEmergencyNumberList()` доступен на API 29+ (с fallback hardcoded map для нижних API).

## Local Test Path *(mandatory)*

- **Emulator / device**:
  - JVM unit tests для `CloudAvailability` contract + `LocalAlternative` contract + `EmergencyNumberResolver` fallback logic.
  - `pixel_5_api_34` emulator — install → main screen без Sign-In → local features работают → `cloudAvailable=false` в DataStore.
  - `pixel_5_api_34` emulator — Sign-In flow → `cloudAvailable=true` запись → reboot → флаг сохранён.
  - physical device #1 (currently Xiaomi 11T) — regression FCM token timing + spec 018 ciphertext round-trip + spec 019 FCM smoke.
- **Fake adapters used**:
  - `FakeAuthProvider` (controllable identity emit) — для тестов `CloudAvailability` подписки.
  - `FakeCloudAvailability` (controllable boolean) — для тестов features которые читают флаг.
  - `FakeLocalAlternative` (deterministic result) — для тестов pattern.
  - `FakeEmergencyNumberResolver` (returns fixed number) — для тестов SOS dialer.
- **Verification commands**:
  - `./gradlew :core:cloud:jvmTest --tests "*CloudAvailability*"` — read/write contract + AuthProvider subscription.
  - `./gradlew :core:cloud:jvmTest --tests "*LocalAlternative*"` — contract.
  - `./gradlew :core:cloud:jvmTest --tests "*EmergencyNumberResolver*"` — fallback logic.
  - `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="OfflineFirstE2ETest"` — fresh install no Sign-In.
  - `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="SOSLocalAlternativeE2ETest"` — SOS dialer.
  - `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="SignInExplanationScreenE2ETest"` — explanation flow.
  - `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="CloudConfigEncryptionE2ETest"` — spec 018 regression.
- **Cannot-test-locally gaps**:
  - Regression test на **physical device #1 (currently Xiaomi 11T)** — FCM token timing (FR-013) + spec 018/019 regression. `// TODO(physical-device): pre-merge run regression suite on Xiaomi 11T`.
  - Real Huawei device — emulator override покрывает большинство. `// TODO(physical-device): future Huawei verification when device available`.

## AI Affordance *(mandatory)*

**no AI affordance — internal architectural infrastructure**.

`CloudAvailability`, `LocalAlternative`, `SignInExplanationScreen` — internal infrastructure. AI-agent не должен иметь возможности менять `cloudAvailable` или вызывать `LocalAlternative.executeLocally()` напрямую — это разрушит инвариант продукта.

## OEM Matrix *(mandatory if feature touches device behavior)*

Feature touches: DataStore persistence, `AuthProvider` subscription, `Intent.ACTION_DIAL`, `TelephonyManager`.

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|-------------------------|---------------------|
| Stock Android (Pixel) | baseline | — | emulator `pixel_5_api_34` |
| Samsung One UI | DataStore работает стандартно; `Intent.ACTION_DIAL` стандартно | — | `// TODO(physical-device): Samsung verification when device available` |
| Xiaomi MIUI | DataStore работает; `AuthProvider` через Firebase Auth стандартно | — | physical device #1 (currently Xiaomi 11T) regression test |
| Huawei EMUI | No GMS → AuthProvider Firebase adapter может вернуть null навсегда. `cloudAvailable` остаётся `false`. Локальные features работают. | TASK-49 не делает ничего особого — просто `false` через Observer pattern | `// TODO(physical-device): real Huawei verification when device available` |

## Exit Ramps and Design Reversibility

Этот раздел фиксирует **two-way door classification** этой спеки per CLAUDE.md rule 3.

**Это TWO-WAY DOOR**, не one-way. Минимальная инфраструктура: один булев + один interface + один Composable. Все три могут быть заменены за дни:

1. **Сменить хранение флага** (DataStore → Encrypted SharedPreferences → in-memory only):
   - Cost ~1 day. Меняется только `CloudAvailabilityImpl`.

2. **Сменить trigger механизм** (AuthProvider → собственный server JWT presence):
   - Cost ~2-3 days. Меняется только subscription в `CloudAvailabilityImpl`.
   - При переезде на own server (per CLAUDE.md rule 8): добавляется `OwnServerAuthProvider` adapter в TASK-3 territory; TASK-49 **не меняется**.

3. **Удалить `LocalAlternative` interface** если только SOS его использует, а остальные критические фичи не выбирают этот pattern:
   - Cost ~0.5 day. SOS становится embedded в Wizard preset с прямым dialer Intent.

4. **Заменить `SignInExplanationScreen`** на разные screens для wizard vs Settings:
   - Cost ~0.5 day. Composable копируется, расходится в две версии.

**Что навсегда полезно**:
- `cloudAvailable` булев в DataStore (нейтральный факт «Sign-In есть»).
- Subscription на `AuthProvider` (правильное direction of dependency).
- TASK-5 regression fix (это **bug fix**, не архитектурное решение).

## Dependencies

**Hard dependencies** (должны быть merged до TASK-49):

- **TASK-3 (F-4 AuthProvider)** ✅ — для `identityFlow` subscription. **Pre-requisite check для `/speckit.plan`**: подтвердить что `AuthProvider.identityFlow` exists; если нет — добавить как micro-доработку TASK-3 или включить в scope TASK-49.

**Soft dependencies** (informational):

- TASK-6 — **зависит от** TASK-49 (currently Paused). После TASK-49 closure TASK-6 возобновляется.
- TASK-5 (F-5c FCM push) ✅ — будет **изменён** этой спекой (regression fix FR-013).
- TASK-7 (S-1 Simple Launcher Wizard) ⏸ — будет использовать `SignInExplanationScreen` в опциональном шаге wizard.
- decision 2026-06-15-deferred-cloud/01 — реализуется этой спекой.

## Out of Scope

- **Network state observation** (ConnectivityManager) — concern других фич если им нужно.
- **GMS availability check** — concern других фич если им нужно.
- **Token expiry / refresh handling** — Firebase SDK сам.
- **CloudActionGate port / abstraction** — намеренно НЕ строим (CLAUDE.md rule 4 MVA).
- **Central CloudFeatureRegistry manifest** — намеренно НЕ строим.
- **`CloudMode` enum для классификации фич** — намеренно НЕ строим.
- **Inventory всех будущих фич** — каждая S-задача сама решит при своём clarify.
- iOS adapter для `CloudAvailability` — Phase 4 (TASK-26).
- Subscription billing gates — TASK-15.
- Real-time UI переключение при state change — фичи сами через reactive Flow.
- `ACTION_CALL` для SOS auto-dial — НЕ используем (требует `CALL_PHONE` permission). User тапает «вызов» в dialer.
- Encryption DataStore — НЕ нужно (булев не sensitive data).

## Constitution Gates (предварительно — финализируются в `/speckit.plan`)

- **Rule 1** (domain isolated): `CloudAvailability`, `LocalAlternative`, `ActionContext`, `ActionResult` — pure domain в `core/cloud/commonMain`; никаких Android / Firebase / DataStore типов в signatures (DataStore wrap'нут внутри `CloudAvailabilityImpl`).
- **Rule 2** (ACL): DataStore, `Intent.ACTION_DIAL`, `TelephonyManager` — wrap'нуты внутри Android adapters; их типы не вытекают в core.
- **Rule 3** (one-way doors): **TASK-49 = two-way door**. Exit ramps в разделе выше. Cost смены подхода ~1-3 days без переписывания других тасок.
- **Rule 4** (MVA): минимальная инфраструктура. НЕ строим CloudActionGate / CloudFeatureRegistry / CloudMode (нарушали бы MVA — single-implementation interface для будущего).
- **Rule 5** (wire-format): n/a — TASK-49 не вводит wire-format. DataStore — internal cache, не wire-format.
- **Rule 6** (mock-first): `FakeAuthProvider`, `FakeCloudAvailability`, `FakeLocalAlternative`, `FakeEmergencyNumberResolver` — обязательны для unit-tests.
- **Rule 7** (fitness functions): `verifyCloudIsolation` Gradle task: `:core:cloud` depends только на Kotlin stdlib + coroutines.
- **Rule 8** (server-roadmap): при переезде на own server — `OwnServerAuthProvider` adapter добавляется в TASK-3 territory (Observer pattern), TASK-49 НЕ меняется. Inline TODO в documentation FR-015 — «при переезде на own server: добавить SRV-AUTH-001 в server-roadmap.md, OwnServerAuthProvider реализует тот же `identityFlow` port».

## References

- [decision 2026-06-15-deferred-cloud/01](../../docs/product/decisions/2026-06-15-deferred-cloud/01-deferred-sign-in.md) — primary source.
- [CLAUDE.md](../../CLAUDE.md) — rules 1, 2, 3, 4, 7, 8.
- [checklist-device-self-sufficiency](../../.claude/skills/checklist-device-self-sufficiency/SKILL.md) — validates compliance.
- [backlog TASK-49](../../backlog/tasks/task-49%20-%20Cloud-Feature-Inventory-Offline-First-Architecture.md) — portfolio entry.
- [backlog TASK-6 Paused](../../backlog/tasks/task-6%20-%20F-5-Root-Key-Hierarchy-Owner-Recovery.md) — заблокирована этой спекой.
- [backlog TASK-5 F-5c](../../backlog/tasks/task-5%20-%20F-5c-FCM-config-updated-push-trigger.md) — будет regression-fixed.
- Memory: `project_deferred_cloud_architecture`, `project_spec_017_018_019_e2e_working`.

---

## Plain Russian summary (для не-разработчика владельца)

**Что закрывает TASK-49 (финальная минимальная версия)**: даёт **один булев флаг** «работаем с облаком или нет», который лежит в файле на диске телефона. Меняется только на двух событиях: пользователь вошёл через Google → `true`. Пользователь вышел → `false`. Никаких других проверок (нет интернета, нет Google-сервисов, истёк токен) — это не задача TASK-49, это решают другие фичи если им надо.

**Что строим (3-5 дней работы)**:

1. **Булев флаг `cloudAvailable`** в DataStore (стандартный файл настроек Android).
2. **Подписка на `AuthProvider`** (из уже сделанной TASK-3) через oповещалку — меняет флаг при Sign-In/Sign-Out.
3. **Единый экран `SignInExplanationScreen`** — объясняет «зачем войти + что станет доступно» + кнопки «Войти / Отмена». Используется одинаково в wizard'е и Settings.
4. **Интерфейс `LocalAlternative`** + один example: `SOSDialerAlternative` — SOS всегда открывает dialer с emergency-номером (112/911/102 по стране), независимо от cloud-state.
5. **Регрессионный fix** TASK-5: FCM token уходит на сервер только при `cloudAvailable=true`.
6. **Документация** на простом русском.

**Что НЕ делаем**:
- НЕ проверяем сеть.
- НЕ проверяем GMS.
- НЕ проверяем token expiry.
- НЕ делаем CloudActionGate / CloudFeatureRegistry / CloudMode (это были over-engineering, убрали).

**После TASK-49**: TASK-6 расPaused'ится и продолжит реализацию Recovery Backup, используя `cloudAvailable` флаг для своих решений.

**Главное обещание** (навсегда): каждое устройство **самодостаточно**, cloud — это **upgrade**.
