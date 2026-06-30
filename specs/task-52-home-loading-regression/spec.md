# Feature Specification: HomeActivity loading regression — устранение вечной «Загрузка…» после wizard'а

**Feature Branch**: `task-52-home-loading-regression`
**Created**: 2026-06-26
**Status**: Draft
**Backlog Task**: TASK-52
**Depends on**: TASK-7 (Simple Launcher first-run + Setup Wizard, Done — фиксим регрессию)

**Input**: Bug-fix спека. После завершения wizard'а на Xiaomi 11T (Android 12, MIUI) главный экран `HomeActivity` показывает вечную надпись «Загрузка…» в центральной области, плитки никогда не появляются. Нижняя панель табов (BottomFlowBar) при этом видна — проблема локализована в области flow slot'а внутри `HomeScreen`. Нужно: (1) диагностировать root cause, (2) пофиксить инициализацию flow slot'а, (3) заменить вечный спиннер на state machine `Loading → Ready | Error` с error UI и кнопкой Retry.

## Clarifications

### 2026-06-26 — Pre-plan clarification pass

| # | Question | Resolution |
|---|----------|------------|
| 1 | Сколько и каких плиток показывает `simple-launcher` пресет в дефолте? | **6 плиток** из bundled `classic-6.json`: Phone / Messages / Camera / Gallery / Contacts / Settings. Прежний черновик spec ошибочно перечислял «Телефон / SOS / Сообщения / Фото / Настройки / Помощь» — исправлено по факту bundled манифеста. |
| 2 | Какой timeout до перехода `Loading → Error`? | **3 секунды** — текущий целевой baseline (Xiaomi 11T, эмулятор pixel_5_api_34) уверенно укладывается. Слабые устройства не покрываются этой спекой явно. |
| 3 | Делать ли silent auto-retry перед показом Error? | **Нет**. MVA principle (CLAUDE.md rule 4): не усложняем, пока не доказано что transient retry помогает. Если plan диагностирует race condition как root cause — auto-retry можно добавить как follow-up. |
| 4 | Ставить ли cap на количество ручных retry'ев? | **Нет cap**. Обе кнопки (Retry / Сброс) всегда видны, пользователь сам выбирает когда сдаться. |
| 5 | Как сохранять `HomeLoadingState` через `Activity.recreate()`? | **Decompose `retainedInstance`** — стандартный механизм проекта, retain'ит компонент с его `MutableStateFlow` сквозь recreation. Никаких новых абстракций (`savedInstanceState` / ViewModel). |
| 6 | Показывать ли technical reason ошибки пользователю? | **Нет**. Elderly-friendly UX: пользователь видит только «Не удалось загрузить настройки». Technical reason пишется в logcat (и persistent log, если есть). |
| 7 | Нужен ли confirmation dialog перед «Сбросить настройки и пройти заново»? | **Да**. Destructive операция должна спрашивать — стандартная UX практика и защита от случайного тапа пожилым пользователем. Текст диалога: «Все настройки будут стёрты. Продолжить?» с кнопками «Сбросить» / «Отмена». |

## Контекст и цель

`HomeActivity` — главная точка входа в приложение после первого запуска. После того как пользователь завершил wizard и выбрал пресет (например, `simple-launcher` для пожилых), `HomeActivity` должна показать **6 плиток** дефолтного набора `classic-6` (Phone / Messages / Camera / Gallery / Contacts / Settings — actionType из `core/src/androidMain/assets/wizard/tile-sets/classic-6.json`) в течение 1–3 секунд. На целевом устройстве этого не происходит: экран **постоянно** показывает текст «Загрузка…» в центральной области, пользователь не может попасть ни на одну плитку. Поведение не зависит от сети, кэша, повторного запуска приложения.

Это **блокирующий bug** для всей Phase 1: TASK-7 формально помечен Done, но end-to-end сценарий «пользователь установил приложение → проходит wizard → пользуется главным экраном» не работает на реальном целевом устройстве.

Цель фичи — вернуть end-to-end сценарий в рабочее состояние и добавить **failure-recovery UX** (вместо вечного спиннера) чтобы любой будущий сбой загрузки настроек был виден пользователю и предлагал способ выхода.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Свежеустановленный пользователь видит главный экран (Priority: P1)

Пожилой пользователь (`primary user`) или его родственник впервые открывает приложение, проходит wizard и попадает на главный экран с плитками за разумное время (≤ 3 секунд).

**Why this priority**: Это блокирующий путь — без него приложение нерабочее. Все остальные фичи Phase 2 (контакты, SOS, синхронизация) бесполезны, пока главный экран не загружается.

**Independent Test**: На свежей установке APK на Xiaomi 11T или эмуляторе pixel_5_api_34: запустить → пройти wizard → засечь время до появления плиток секундомером.

**Acceptance Scenarios**:

1. **Given** свежеустановленное приложение на Xiaomi 11T, **When** пользователь запускает приложение, выбирает пресет «Простой лаунчер для пожилых» в first-launch экране и проходит wizard до конца, **Then** в течение 3 секунд после нажатия «Готово» появляется главный экран с 6 плитками `classic-6` (Phone / Messages / Camera / Gallery / Contacts / Settings).
2. **Given** то же на эмуляторе pixel_5_api_34, **When** свежая установка → wizard → finish, **Then** главный экран с 6 плитками `classic-6` появляется в течение 3 секунд.
3. **Given** пользователь закрыл приложение (kill) после первого успешного запуска, **When** открывает заново, **Then** главный экран появляется в течение 1 секунды и wizard не запускается повторно.

---

### User Story 2 — Понятная обратная связь при сбое загрузки настроек (Priority: P1)

Если по какой-то причине (повреждённый конфиг, race condition, future-regression) настройки главного экрана не загрузились, пользователь видит **error UI** с понятным текстом и кнопками выхода — не вечный спиннер.

**Why this priority**: Same priority как P1 — без неё «Загрузка…» 2026-06 повторится при любом будущем regression-баге, а пожилой пользователь не сможет ничего сделать с «зависшим» экраном.

**Independent Test**: Unit-тест с fake `FlowRepository`, возвращающим пустой результат → проверить что `HomeComponent.state` переходит в `Error` через таймаут, а UI показывает error UI с двумя кнопками.

**Acceptance Scenarios**:

1. **Given** `FlowRepository` возвращает пустой список (симуляция сбоя через DI override), **When** `HomeActivity` запускается, **Then** в течение **3 секунд** «Загрузка…» сменяется на error UI с текстом «Не удалось загрузить настройки», кнопкой «Попробовать снова» и кнопкой «Сбросить настройки и пройти заново». Technical reason пользователю **не показывается** (только в logcat).
2. **Given** error UI открыт, **When** пользователь нажимает «Попробовать снова», **Then** запускается новая попытка загрузки настроек; если успешно — появляется главный экран; если снова сбой — error UI показывается снова. Cap на количество повторов **нет** — пользователь может нажимать сколько хочет.
3. **Given** error UI открыт, **When** пользователь нажимает «Сбросить настройки и пройти заново», **Then** показывается confirmation dialog «Все настройки будут стёрты. Продолжить?» с кнопками «Сбросить» / «Отмена».
4. **Given** confirmation dialog показан, **When** пользователь нажимает «Сбросить», **Then** запускается `FirstLaunchActivity` через тот же путь что и `onResetData` в `HomeActivity`. **When** пользователь нажимает «Отмена», **Then** dialog закрывается, error UI остаётся видимым.

---

### User Story 3 — Smoke-тапы по плиткам после загрузки (Priority: P2)

Все 6 плиток `simple-launcher` пресета должны быть тапабельны и открывать соответствующий экран без крэшей.

**Why this priority**: Это smoke-проверка что fix не сломал существующую функциональность плиток (нижестоящая логика — TASK-7 territory).

**Independent Test**: На свежей установке после wizard'а — тап по каждой из 6 плиток по очереди, проверить что соответствующий экран открывается и приложение не падает.

**Acceptance Scenarios**:

1. **Given** главный экран с 6 плитками `classic-6` загружен, **When** пользователь тапает плитку «Phone» (`phone.call`), **Then** открывается экран контактов / номера набора без крэша.
2. **Given** то же, **When** пользователь тапает «Settings» (`settings.open`), **Then** открывается экран настроек.
3. **Given** аналогично для оставшихся 4 плиток (`messages.open`, `camera.open`, `gallery.open`, `contacts.open`) — нет крэшей, соответствующий action отрабатывает (или явно failes с error UI если backing app не установлен — это TASK-7 territory, не блокирует SC).

---

### Edge Cases

- **Race condition между WizardActivity.finish() и HomeActivity.onCreate**: что если пресет ещё не записан в `presetRepository` к моменту запуска `HomeActivity`? Должен быть **детерминистический порядок** (либо blocking await перед startActivity, либо state machine wait в HomeComponent с таймаутом).
- **Активный preset существует, но его flows config повреждён** (пустой JSON, неверная schema-version): `FlowRepository.getFlows()` возвращает пустой список. UI должен перейти в `Error`, не зависать.
- **Активный preset отсутствует совсем** (например, external HOME-intent до wizard'а): уже обрабатывается — `activePreset?` nullable. Этот случай **out of scope** для бага (он уводит в `FirstLaunchActivity`).
- **Cold start vs warm start**: при cold start (process death) loading может быть медленнее. Таймаут 3 секунды должен это покрывать.
- **Слабое устройство**: на устройствах слабее Xiaomi 11T (которое мы целим как baseline) загрузка может занять > 1 секунды. Loading state должен быть видим, не «мгновенный flash».
- **Recreate (rotation, theme change)**: `HomeActivity` пересоздаётся через `recreate()` — state машина должна корректно отработать второй раз.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `HomeComponent` MUST публиковать состояние загрузки главного экрана через `StateFlow<HomeLoadingState>` где `HomeLoadingState ∈ { Loading, Ready, Error }`.
- **FR-002**: При запуске `HomeComponent` MUST войти в состояние `Loading`, начать загрузку flows из `FlowRepository`, и при наличии непустого результата перейти в `Ready` с активным flow.
- **FR-003**: Если `FlowRepository.getFlows()` возвращает пустой список ИЛИ не возвращает ничего в течение **3 секунд** от старта `HomeComponent` — MUST произойти переход в состояние `Error(reason)`.
- **FR-004**: `HomeScreen` Composable MUST отображать разный UI в зависимости от `HomeLoadingState`:
  - `Loading` → текст «Загрузка…» (как сейчас) или spinner;
  - `Ready` → `FlowScreen(active)` (как сейчас);
  - `Error` → текст «Не удалось загрузить настройки» (без technical reason, см. FR-012), кнопки «Попробовать снова» и «Сбросить настройки и пройти заново».
- **FR-005**: Кнопка «Попробовать снова» MUST триггерить повторный вызов `FlowRepository.getFlows()` без перезапуска приложения. Cap на количество повторов **отсутствует** — пользователь может нажимать сколько хочет; обе кнопки (Retry / Сброс) остаются видны постоянно.
- **FR-005a**: Silent auto-retry перед показом Error **не делается** — после первого timeout (3s) сразу показывается error UI. (Per Clarification Q3, MVA principle.)
- **FR-006**: Кнопка «Сбросить настройки и пройти заново» MUST показывать confirmation dialog с текстом «Все настройки будут стёрты. Продолжить?» и кнопками «Сбросить» / «Отмена». Только при подтверждении «Сбросить» MUST триггериться тот же путь что текущий `onResetData` в `HomeActivity` (→ `FirstLaunchActivity` с `CLEAR_TASK`). При «Отмена» dialog закрывается, error UI остаётся.
- **FR-007**: Активный пресет (выбранный в wizard'е) MUST быть гарантированно записан в `presetRepository` **ДО** того как стартует `HomeActivity` (либо детерминистический порядок в `WizardActivity.finish()`, либо blocking-await на стороне `HomeActivity.onCreate`).
- **FR-008**: Существующий `runBlocking { presetRepository.getActivePreset() }` в `HomeActivity.onCreate` (если он часть проблемы) MUST быть заменён либо на правильную последовательность шагов в wizard'е, либо на корректную suspend-инициализацию через `HomeComponent` state machine.
- **FR-009**: При повторном запуске приложения (kill + open) `HomeComponent` MUST перейти в `Ready` в течение **1 секунды** (не 3 — это не cold-start от нуля).
- **FR-010**: Состояние `HomeLoadingState` MUST переживать `recreate()` `Activity` (config change, theme switch) через **Decompose `retainedInstance`** — `HomeComponent` retain'ится с его `MutableStateFlow<HomeLoadingState>` без `savedInstanceState` / ViewModel. После recreate UI MUST продолжить с того же состояния (Ready остаётся Ready) без повторного спиннера.
- **FR-011**: Тексты error UI и confirmation dialog'а MUST быть локализованы на **RU и EN** (две локали, остальные — out of scope, отложены на `procedure-translate-spec-strings`).
- **FR-012**: Technical reason из `Error(reason: String)` MUST логироваться в logcat с уровнем WARN/ERROR, но MUST НЕ показываться пользователю в UI. (Per Clarification Q6, elderly-friendly UX.)

### Key Entities

- **HomeLoadingState**: sealed class в `core/src/commonMain/.../ui/navigation/`. Варианты: `Loading`, `Ready(activeFlowId)`, `Error(reason: String)`. Pure-Kotlin, никаких Android типов (rule 1 domain isolation). `reason` — internal technical detail, в UI не показывается (FR-012).
- **HomeComponent**: уже существует, расширяется новым `MutableStateFlow<HomeLoadingState>` и логикой timeout 3s. Retain'ится через Decompose `retainedInstance` сквозь Activity recreation (FR-010).
- **HomeResetConfirmationDialog**: новый Composable. Показывается поверх error UI при нажатии «Сбросить настройки и пройти заново». Два state: visible / hidden. Тексты RU + EN.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: На свежей установке APK на Xiaomi 11T (Android 12, MIUI) главный экран с 6 плитками отображается в течение **3 секунд** после нажатия «Готово» в wizard'е.
- **SC-002 [backlog]**: На свежей установке APK на эмуляторе `pixel_5_api_34` — то же поведение (≤ 3 секунды до плиток).
- **SC-003 [backlog]**: Если настройки не загрузились (симулируется через DI override / fake repository), пользователь видит error UI с текстом ошибки и кнопкой Retry, а не вечную «Загрузка…».
- **SC-004 [backlog]**: Все 6 плиток `classic-6` tile-set'а (Phone / Messages / Camera / Gallery / Contacts / Settings) видимы и тапабельны после первого запуска. Если bundled tile-set обновится (например, на `classic-9`) — AC обновляется автоматически: «все плитки текущего bundled tile-set'а».
- **SC-005 [backlog]**: Повторный запуск приложения (kill + open) показывает главный экран без перепрохождения wizard'а и без задержки > **1 секунды**.
- **SC-006**: Unit-тест `HomeComponentLoadingStateTest` покрывает 3 transition'а: `Loading → Ready` (happy path), `Loading → Error (timeout)`, `Error → Loading → Ready` (retry success). Тест зелёный на JVM.
- **SC-007**: Cold start time приложения после fix'а **не ухудшается** относительно baseline более чем на 200 ms (измеряется на pixel_5_api_34, 3 прогона, median).
- **SC-008**: Logcat trace зафиксирован в `research.md` для двух сценариев (Xiaomi и emulator) — содержит lifecycle события между `WizardActivity.finish()` и первой рекомпозицией `HomeScreen`.

## Assumptions

- Целевое устройство Xiaomi 11T (Android 12, MIUI 13) считается baseline. Более слабые устройства не покрываются этой спекой явно — таймаут 3 секунды должен быть достаточным запасом для большинства Android 9+ устройств.
- Эмулятор `pixel_5_api_34` доступен и работает (per memory `reference_compose_ui_test_api_mismatch.md` для UI тестов нужен AVD ≤ API 34 — если потребуются compose-ui-tests, использовать api_34).
- Wizard на TASK-7 ветке работает корректно до момента нажатия «Готово» — мы фиксим только loading главного экрана, не wizard как таковой.
- `simple-launcher` пресет в bundled config содержит **6 плиток** в дефолтном `classic-6` tile-set'е (verified 2026-06-26 через `core/src/androidMain/assets/wizard/tile-sets/classic-6.json`): `phone.call`, `messages.open`, `camera.open`, `gallery.open`, `contacts.open`, `settings.open`. Если bundled tile-set обновится (например, до 9 плиток в `classic-9` для Senior-warm) — SC-004 рендерится из текущего bundled, не из spec hardcode.
- Существующая 7-tap admin gate механика остаётся работать в Loading state (не блокируется) — admin может сбросить даже если экран не загрузился. Это **assumption**, проверить при diagnostics.
- Cold start time baseline существует или будет измерен на pixel_5_api_34 в начале работы (3 прогона до fix) для последующего сравнения.

## Local Test Path *(mandatory)*

- **Emulator / device**: `pixel_5_api_34` через skill `android-emulator` для smoke; Xiaomi 11T (`physical-device`) для финальной проверки.
- **Fake adapters used**: `FakeFlowRepository` (новый, возвращает заданный список flows или пустой); `FakePresetRepository` (существующий, если есть; иначе создаётся минимальный для теста).
- **Fixtures / seed data**: Стандартный `simple-launcher` bundled JSON (без модификаций); fake repository hardcoded в unit-тесте.
- **Verification command**:
  - Unit: `./gradlew :core:testDebugUnitTest --tests "*HomeComponent*"` — должен включать `HomeComponentLoadingStateTest` (3 transition'а).
  - Instrumentation (опционально): `./gradlew :app:connectedRealBackendDebugAndroidTest --tests "*HomeActivityLoadingTest*"` на pixel_5_api_34.
  - Manual smoke: `./gradlew :app:installRealBackendDebug` → запуск → wizard → секундомер до плиток.
- **Cannot-test-locally gaps**:
  - Xiaomi 11T physical-device verification (`[deferred-physical-device]`) — owner запускает руками после merge PR.
  - Cold start time measurement в строгом виде — `[deferred-local-emulator]` через benchmark, не unit.

## AI Affordance *(mandatory)*

No AI affordance — internal bug-fix в UI layer. State machine не предоставляет capability'ов для будущих AI-агентов, это чисто UI lifecycle fix.

## OEM Matrix *(mandatory if feature touches device behavior)*

Фича затрагивает Android-specific lifecycle (Activity recreation, Decompose component lifecycle, runBlocking в onCreate), но **не** background work / permissions / launcher role / notifications / battery / autostart. OEM-специфика релевантна только в части того что **проявление бага** наблюдалось на Xiaomi MIUI — поэтому таблица заполняется как verification surface, не как mitigation matrix.

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|-------------------------|---------------------|
| Stock Android (Pixel) | baseline | детерминистический порядок init + state machine с таймаутом | emulator `pixel_5_api_34` |
| Xiaomi MIUI | **баг наблюдался впервые здесь** — возможны MIUI-specific lifecycle квирки (aggressive process kill, deferred broadcast delivery) | fix не зависит от платформенных хаков, работает через state machine с таймаутом | Xiaomi 11T physical device (`[deferred-physical-device]`) |
| Samsung One UI | не проверяется в этой спеке | — | none — owner может проверить опционально |
| Huawei EMUI | не проверяется в этой спеке (нет устройства) | — | none |

---

## Out of Scope

- Изменение wizard flow logic (TASK-7 уже Done, фиксим только регрессию loading'а главного экрана).
- Локализация error UI на 10 языков (только RU + EN в этой спеке; остальные — через `procedure-translate-spec-strings` в follow-up).
- Telemetry / crash reporting об ошибке загрузки (Sentry — TASK-37, future).
- Animations / shimmer placeholder для Loading state (cosmetics).
- Другие preset'ы кроме `simple-launcher` (workspace, launcher) — отдельный smoke если время позволит.
- Полная переработка `HomeActivity.onCreate` от `runBlocking` к suspend-инициализации, если можно ограничиться более точечным fix'ом.
