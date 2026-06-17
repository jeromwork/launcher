# Feature Specification: Wizard Module + Localization + Senior UI Kit (F-3)

**Feature Branch**: `015-wizard-localization-senior-ui`
**Created**: 2026-06-16
**Status**: InProgress (implementation landed 2026-06-17; pending emulator smoke + AI translation refresh)
**Roadmap ID**: F-3 (Phase 1, шаг 1 после order-shift 2026-06-15 v2)
**Input**: [`docs/product/roadmap.md` §Шаг 1 F-3](../../docs/product/roadmap.md#L761) + [`docs/product/glossary.md`](../../docs/product/glossary.md) (terminology + JSON header + reusability discipline §7a) + decision [`2026-06-15-deferred-cloud/`](../../docs/product/decisions/2026-06-15-deferred-cloud/) (local-first) + [ADR-004 localization](../../docs/dev/adrs/) + CLAUDE.md rules 1, 2, 4, 5, 6, 7, 9.

> **Terminology contract (glossary §1 + §8):** в этом спеке слово «preset» **не используется**. Применяются: **app-family** (тип приложения — Simple Launcher / Admin App / TV), **layout-grid** (параметр шага `GridSelectionStep`), **`tile.set` / `screen.layout` / `wizard.manifest`** (три bundled JSON-схемы).

---

## Контекст и цель спека

F-3 — **первый** шаг Phase 1 (order shift 2026-06-15 v2). До F-3 launcher не имеет first-run flow, локализации и senior-friendly UI примитивов. После F-3:

1. Каждая будущая **app-family** (Simple Launcher S-1, Admin App S-2, TV S-10, Caregiver V-6) запускает свой first-run flow поверх **одного** `WizardEngine`, объявляя только свой `wizard.manifest`.
2. Все user-facing строки проходят через локализационный layer; CI не пускает в master строку без переводов на 10 поддерживаемых языков.
3. UI Kit с большими тапами / warm-contrast темой / fontScale-aware утилитами лежит в отдельном модуле, готовый к переиспользованию.

Дополнительная цель — **reusability discipline** (glossary §7a): три модуля проектируются как **launcher-agnostic extraction candidates** для будущей экосистемы (Elderly-Friendly Messenger V-2, Family Album V-3 — Phase 4). Экстракт в отдельный репозиторий **не выполняется сейчас** (rule of three), но **lint rule** структурно гарантирует launcher-агностичность с дня 1.

**Ключевые границы:**

- F-3 даёт **framework**, не конкретные manifest'ы / `tile.set` / `screen.layout` (это S-1 / S-2 поставят как bundled JSON в `app/`).
- Wizard работает **локально**: нет Google Sign-In, нет cloud, нет сети.
- `PairingStep` — **stub** в F-3 (real integration со спекой 007 — в S-2).
- Server-side источники (`NetworkConfigSource`) — additive adapter позже (glossary §5.3), не F-3.

---

## Clarifications

### 2026-06-16 — Pre-clarify mentor session (зафиксированные решения)

Решения, принятые в mentor-обсуждении 2026-06-16 до запуска `/speckit.clarify`. Каждое weave'нуто в соответствующие FR/Assumptions ниже.

| # | Вопрос | Резолюция |
|---|----------|-----------|
| C-1 | Что приоритетнее — скорость F-3 или прочность решений? | **Прочность.** Закрываем максимум архитектурных решений сейчас, plan.md задерживается на пару дней. |
| C-2 | Ecosystem reuse (messenger V-2, album V-3) — насколько серьёзно? | Messenger/album планируется в Phase 4 как часть экосистемы заботы о пожилых. Launcher имеет ценность standalone, но экосистема закрывает больше болей. Reuse — **цель, не жёсткое требование**: «reuse если cheap, separate проект если сложно». |
| C-3 | iOS / TV — F-3 должен закладывать поддержку сейчас? | **REVISED 2026-06-17 после pre-flight**: iOS targets **уже включены** в проекте (iosX64, iosArm64, iosSimulatorArm64 per `core/build.gradle.kts`); CMP — обязательный UI стек per [ADR-005](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md). F-3 пишет всё в `commonMain` Compose Multiplatform; iOS support получается **автоматически**. TV — отдельная спека когда понадобится. |
| C-4 | Wizard engine — pure state machine или Compose host? | **REVISED 2026-06-17**: `core/wizard/` engine + step Composables все в `commonMain` Compose Multiplatform per [ADR-005](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md). Pure state machine + Compose host **слиты** — Composable steps работают на всех платформах одинаково. |
| C-5 | Куда едут wizard preferences (theme/fontScale/language override) — расширяем спеку 008 ConfigDocument или отдельный store? | **F-3 implementation: `UserPreferencesStore` local-only.** Goal state — admin remote-меняет UX preferences бабушки (вариант «расширяем 008»), но это требует cloud sync (F-4 + спека 008 extension). В F-3 cloud отсутствует. **Inline-TODO**: migration `UserPreferencesStore` → `ConfigDocument.userPreferences` когда F-4 + sync infrastructure ready. Спека 008 в F-3 НЕ трогается. |
| C-6 | Base language для строк локализации? | **English (EN).** Source of truth — `en/strings.xml`. Все 11 переводов генерируются из EN. Override от ADR-004 явно зафиксирован в этой спеке (см. FR-030). |
| C-7 | Module structure для F-3? | **REVISED 2026-06-17 после pre-flight**: НЕ создаём 3 новых модуля. F-3 добавляет **пакеты внутри существующего `:core` KMP-модуля** (per [ADR-005](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md) — uno core, разделение через пакеты): `com.launcher.api.wizard`, `com.launcher.api.localization`, `com.launcher.ui.senior`. Adapter implementations — `com.launcher.adapters.wizard` (and others) per existing convention (`api/setup` от spec 010 demonstrates pattern). Все в `commonMain` Compose Multiplatform. |
| C-8 | String resources library? | **REVISED 2026-06-17**: **Compose Multiplatform Resources** (`compose.components.resources`) — **уже** подключено в `core/build.gradle.kts:commonMain`. moko-resources отвергнут (двойная dependency бессмысленна). Compose Resources — Google official, KMP-native, supports plurals + RTL + screenshots. |
| C-9 | Supported languages? | **11 языков** (EN base + 10 переводов): EN, RU, ES, ZH, AR, HI, PT, DE, FR, JA, **kk-Latn** (казахский латиница — future-proof per Казахстан переход на латиницу к 2031). Owner decision 2026-06-15 «все языки с дня 1» подтверждён, KK добавлен. |
| C-10 | Translation pipeline? | **Claude как переводчик через skill `procedure-translate-spec-strings`**, запускается в конце `speckit-tasks` orchestrator (async, не блокирует dev loop). Translation memory = git history `<lang>/strings.xml` файлов. Per-key context = `CONTEXT.json` (mandatory для каждого нового key). Canonical терминология = `GLOSSARY.md`. Human review для AR/HI/ZH/JA/KK — Phase 4 или при первом native feedback. |
| C-11 | `actionType` в `tile.set` — validation на load? | **Soft validation** (default config opaque string, soft warning при unknown `actionType`). F-3 принимает `actionTypeValidator: (String) -> ValidationResult` как опциональный constructor parameter; S-1 (когда capability registry runtime F-2 появится) внедрит реальный validator. Inline-TODO: «sync с capability registry — спека 005». |
| C-12 | Структура `wizard.manifest.body.steps[]`? | Sealed class на `stepType` + `params: Map<String, JsonElement>`. Сохраняет forward-compat additive policy (новый step type не требует bump `schemaVersion`). |
| C-13 | `WizardEngine` при `schemaVersion > known`? | **Hard-fail** с понятным сообщением + кнопка «Понятно» (паттерн из спека 010 FR-042). В bundled-only MVP этот случай не должен возникать, но reader защищён. |
| C-14 | JSON Schema validation для CI? | Ручные roundtrip-тесты (Kotlin Serialization compile-time типы). Auto-generated JSON Schema files добавим если drift возникнет реально (rule 4 MVA). |
| C-15 | Lint rule «core/* → app/*» — implementation? | **REVISED 2026-06-17**: **Konsist уже подключен** в проекте (`libs.versions.toml` + `core:androidUnitTest`); spec 005 §8 declared fitness functions через Konsist. F-3 пишет Konsist тесты в **`androidUnitTest`** source set (per existing pattern), **не commonTest** (Konsist JVM-only). Rule shape: package-based вместо module-based — «`com.launcher.ui.*` → `com.launcher.api.wizard.*` forbidden», etc. |
| C-16 | Inter-package dependency directionality (внутри `:core`)? | **REVISED 2026-06-17**: **Directional граф между пакетами** (не модулями). `com.launcher.api.wizard` → `com.launcher.api.localization` (через `StringResolver` port) — OK. `com.launcher.api.wizard` → `com.launcher.ui.senior` — **forbidden** (wizard engine pure logic, UI consumer composes). `com.launcher.ui.senior` self-contained (только Compose primitives, без api/* deps). Verified Konsist в androidUnitTest. |
| C-17 | Apple Developer Account / iOS signing infrastructure? | **Отложено** до материализации iOS launcher. Owner оплатит ($99/year) когда iOS код начнёт писаться. Не блокирует F-3. |
| C-18 | System settings — три типа (standard permissions, special permissions, accessibility-level deep settings) — как моделируются? | Generic `SystemSettingPort` + `SystemSettingStep` (заменяет/обобщает старый `PermissionStep`). Каждое setting имеет `mechanism: SettingMechanism` (StandardPermission / SpecialPermission / AccessibilityService / DeepLink / InAppOnly), `detectionStrategy` (Programmatic / SelfAttest / Indeterminate). См. Part K. |
| C-19 | Pool of available system settings — где живёт? | **Четвёртая JSON-схема** `system-settings.pool` с тем же 6-полевым header'ом. Pool централизован, независим от `wizard.manifest`. Один pool — много manifest'ов референсят. См. Part K + FR-053. |
| C-20 | Detection self-attestation — куда писать факт «пользователь подтвердил, что сделал»? | В `UserPreferencesStore` через subsection `attestedSettings: Map<SettingId, AttestationRecord>` (timestamp + boolean). Если потом понадобится audit — выделим отдельный `AttestationStore`. |
| C-21 | AccessibilityService implementation — F-3 поставляет skeleton? | **Нет.** F-3 даёт `SystemSettingPort` (abstract) + `AndroidSystemSettingAdapter` (умеет открыть deep-link на Settings и проверить активацию через `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`). Конкретный AccessibilityService class (что блокировать) — S-1 ownership. Inline-TODO. |
| C-22 | Cross-app awareness между launcher и messenger / album? | **Не нужен.** System settings — глобальные на устройстве, каждое app делает свой `SystemSettingPort.status(...)` check, получает правдивый ответ от системы. Нет shared store, нет IPC. |
| C-23 | iOS / TV system settings pools — пустые файлы сейчас или отсутствие? | **Отсутствие.** Только Android pool в F-3. iOS / TV pools — в отдельных спеках когда первый iOS / TV consumer материализуется. Inline-TODO в `android-pool.json`. |
| C-24 | Wizard scope — какие wizard kinds покрывает F-3? | **Setup wizard** (комбинирует system settings + UI customization в одном wizard.manifest). Wizard для UX polish (debounce sensitivity, dwell-time) — **отложен** в future spec («UX customization wizard»). Pools пишем как parallel JSONs (system-settings.pool + ui-customization.pool), без universal framework (rule 4 MVA — extract при появлении 3-го wizard kind). |
| C-25 | UI customization — pool в JSON или hardcoded Kotlin steps? | **JSON pool** (`ui-customization.pool`). Симметрия с system-settings.pool. `UIChoiceStep(optionId)` потребляет entries из pool. Замещает старые `LanguageStep / ThemeStep / TextSizeStep / GridSelectionStep / ScreenLayoutPickerStep / TileSetPickerStep` (Kotlin classes). Win: завтра можем вынести UI wizard в отдельную спеку без переписывания (только move JSON + step type). |
| C-26 | Wizard abandonment (System Back на step 0) | **Save+continue** (Q-1 confirmed (b) 2026-06-16): Back → диалог «Сохранить прогресс?» → app закрывается; при повторном запуске continue from saved step. Не mandatory wizard. |
| C-27 | WizardCompleted при обновлении app с новыми шагами (delta wizard) | **Manual admin trigger** (Q-2 confirmed (c) 2026-06-16): wizard НЕ перезапускается автоматически; в Settings (скрытых за 7-tap) появляется badge «новая настройка». Для Critical новых шагов — full-screen banner поверх home. Если paired (спека 007/008) — push admin'у на его устройство. F-3 предоставляет API `WizardEngine.diffPending()` (FR-014b); UI = S-1 / спека 010 territory. |
| C-28 | Hard-fail на breaking schemaVersion — `finishAffinity()` или stay open? | **Stay open + Play Store deep-link** (Q-6 confirmed (b) 2026-06-16). См. FR-016. |
| C-29 | PairingStep stub в F-3? | **Нет** (Q-C confirmed 2026-06-16). S-2 при добавлении pairing добавит `PairingStep` через DI Map (FR-009), F-3 не резервирует имя. Чище — нет dead code. |
| C-30 | Library choices (moko-resources + Konsist) — фиксируем или spike? | **Spike 1-2 дня до plan.md** (Q-5 confirmed (b) 2026-06-16). Pre-plan deliverable — proof-of-concept: 1 локализованная строка + 1 lint rule на minimal KMP-проекте. Если работает — фиксируем; если нет — пересматриваем. |
| C-31 | UserPreferences cross-app sharing (care family ecosystem) | **Местно сейчас + inline TODO про ContentProvider** (Q-2 confirmed 2026-06-16). F-3 хранит в private DataStore. Когда мессенджер / album появятся (Phase 4) — отдельная спека добавит shared ContentProvider в `:app/androidMain` для cross-app read. |
| C-32 | Debounce на SeniorButton (Q-4 issue) | **Удалено из F-3** (Q-4 reversed 2026-06-16). Debounce — runtime polish concern, не foundation. Будет в future «UX customization wizard» спеке. |

### 2026-06-17 — Pre-flight reality check (post `/speckit.analyze` re-run)

После запуска `/speckit.analyze` обнаружено, что часть предыдущих clarifications (C-7, C-8, C-15, C-16) **противоречила** существующей архитектуре проекта (per `core/build.gradle.kts`, `libs.versions.toml`, ADR-005). Эти resolutions переписаны выше с маркером **REVISED 2026-06-17**. Добавлены новые resolutions C-33..C-37 фиксирующие реальный стек:

| # | Вопрос | Резолюция |
|---|----------|-----------|
| C-33 | DI framework? | **Koin** — уже выбран per [ADR-005 Amendment 2026-05-07a](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md). `koin-core` в `commonMain`, `koin-android` в `androidMain`. F-3 wires Koin modules для wizard / localization / ui-senior. |
| C-34 | Navigation framework? | **Decompose** — уже выбран per ADR-005 Amendment. F-3 wizard host использует Decompose `Stack<Configuration>` для step navigation; back stack автоматически persistent. Custom Activity не нужен. |
| C-35 | Persistent storage? | **SQLDelight** для structured data (consistent с spec 008 `LocalConfigStore`) + **DataStore Preferences** для simple key-value. F-3 `WizardCheckpointStore`, `DismissedHintsStore`, `UserPreferencesStore` — DataStore Preferences (simple key-value, schemaVersion как один поле). Если структура усложнится — мигрируем в SQLDelight. |
| C-36 | Product flavor compatibility? | F-3 совместима с обоими flavors (`realBackend` + `mockBackend`) per spec 007 design. Wizard работает identically — bundled JSONs одинаковые в обоих, нет flavor-specific code в F-3 packages. |
| C-37 | Screenshot library? | **Roborazzi** (Compose-aware, KMP-friendly). Paparazzi не подходит (Android Views только). Применяется в `androidUnitTest` source set (Robolectric-backed) для SC-006/006a/007. |
| C-38 | Library spike (T001-T003) — нужен? | **REMOVED 2026-06-17**: все library choices уже зафиксированы в проекте (Compose Resources, Konsist, Koin, Decompose). Spike — moot. Phase 0 в tasks.md сводится к 30-min verification task (T001). |

### Открытые вопросы для `/speckit.clarify`

Большинство grey zones закрыто в mentor-сессии выше. Оставшиеся (если есть) будут поднятые после следующего read-through спеки.

---

## User Scenarios & Testing *(mandatory)*

> F-3 — **foundation spec**: end-user видит wizard / локализованный текст / большие кнопки только когда S-1/S-2 их потребят. User stories ниже описывают **наблюдаемые end-user поведения**, которые F-3 делает возможными. Тестируются они через **тестовую app-family** (`test-app-family.manifest.json` в фикстурах) — это часть Local Test Path спека.

### User Story 1 — App-family объявляет flow в JSON, wizard его проходит (Priority: P1)

Разработчик S-1 (Simple Launcher) пишет `wizard.manifest` JSON с 5 шагами: `LanguageStep`, `ThemeStep`, `TextSizeStep`, `GridSelectionStep`, `TileSetPickerStep`. При first-run пользователь видит эти 5 шагов в указанном порядке, каждый шаг — отдельный экран с заголовком, описанием, контролами выбора и кнопками «Назад» / «Далее». После последнего шага wizard производит первый ConfigDocument из выбранного `tile.set` + ответов пользователя и завершается.

**Why this priority**: без US-1 ни одна app-family не имеет first-run flow — продукт не запускается. Это **single most critical** capability F-3.

**Independent Test**: положить тестовый `test-app-family.manifest.json` с 3 шагами (Theme, TextSize, TileSet) в фикстуры, запустить `WizardEngine.run(manifest)`, симулировать ответы пользователя, проверить что (a) шаги отрисовываются в порядке manifest'а, (b) после last step возвращается `WizardOutcome.Completed(initialConfig)` с непустым ConfigDocument.

**Acceptance Scenarios**:

1. **Given** валидный `wizard.manifest` с 5 шагами в `commonMain/resources/wizard-manifests/test-app-family.json`, **When** WizardEngine стартует с этим manifest'ом, **Then** UI показывает первый шаг (по `steps[0].stepType`), кнопка «Далее» переходит к `steps[1]`, и так до конца.
2. **Given** wizard на шаге 3 из 5, **When** пользователь нажимает «Назад», **Then** UI возвращается к шагу 2 с **сохранёнными** ответами (не сбрасывает выбор).
3. **Given** wizard завершён успешно, **When** возвращается `WizardOutcome.Completed`, **Then** в результате есть (a) `initialConfig: ConfigDocument` (плитки из выбранного `tile.set` + screen.layout пользователя), (b) `userPreferences: Map<String, Any>` (язык / тема / fontScale из шагов).
4. **Given** wizard ещё не запущен на устройстве, **When** app открывается первый раз после установки, **Then** WizardEngine автоматически запускается до показа главного экрана.

---

### User Story 2 — Wizard переживает interrupt и продолжает с того же места (Priority: P1)

Бабушка проходит wizard, доходит до шага 3 из 5, отвлекается на звонок, app убит системой (low memory / process death). Через час бабушка возвращается, открывает app — wizard продолжает с шага 3 (не сбрасывается на шаг 1). Все ответы предыдущих шагов сохранены.

**Why this priority**: на low-end Android устройствах process death — обычное явление. Сброс wizard'а после потери прогресса = **классический elderly-frustrating pattern**, ломает первое впечатление от приложения.

**Independent Test**: запустить WizardEngine, ответить на шаги 1-3, имитировать process death (kill процесса), запустить заново, проверить что показан шаг 4 (не шаг 1), и ответы шагов 1-3 присутствуют в state.

**Acceptance Scenarios**:

1. **Given** wizard на шаге 3, ответы шагов 1-2 сохранены в persistent checkpoint, **When** app процесс убит и перезапущен, **Then** WizardEngine читает checkpoint и возобновляет с шага 3.
2. **Given** checkpoint указывает на шаг 4, но в новой версии app у этого `stepType` другая semantics (схема обновлена в дороге), **When** WizardEngine загружает checkpoint, **Then** показывает понятное сообщение «настройка прервана — нужно начать заново» с кнопкой «Начать сначала» (не silent reset, не crash).
3. **Given** wizard завершён (Completed), **When** app перезапускается, **Then** wizard **не** показывается снова (флаг `wizardCompleted` в persistent state).

---

### User Story 3 — Текст в UI на системном языке, fallback корректный (Priority: P1)

Бабушка с system locale `ru-RU` видит весь wizard на русском (заголовки шагов, кнопки «Далее»/«Назад», описания опций). Внук с system locale `ja-JP` видит тот же wizard на японском. Если внутри `tile.set` встретилась `labelKey`, которая забыта в `ja.strings` — UI показывает english fallback (а не сырой ключ типа `tile_set.classic_6.name`).

**Why this priority**: 10-языковая локализация — owner decision (2026-06-15). Без robustного fallback при дырах в переводе UI ломается визуально (показ raw key) или функционально (crash на missing key).

**Independent Test**: установить system locale в эмуляторе на `de-DE`, прогнать wizard через тестовый manifest, убедиться что все строки на немецком; затем умышленно удалить одну строку из `de.strings`, проверить fallback на EN; затем удалить и EN, проверить что UI показывает сам ключ (не crashes).

**Acceptance Scenarios**:

1. **Given** system locale `ru-RU`, все строки в `ru.strings` присутствуют, **When** WizardEngine отрисовывает любой шаг, **Then** весь UI на русском (включая label'ы из выбираемых `tile.set` через `labelKey`).
2. **Given** system locale `ar-SA` (RTL), **When** wizard отрисовывает шаг, **Then** layout зеркалируется (`LayoutDirection.Rtl`), кнопки «Назад» переезжают на правую сторону, текст выровнен по правому краю.
3. **Given** system locale `ja-JP`, в `ja.strings` отсутствует ключ `wizard.text_size.title`, **When** UI запрашивает эту строку, **Then** показывается значение из `en.strings`, диагностический warning логируется (но не failed build — это **runtime** fallback, не CI rule).
4. **Given** ни в `ja.strings`, ни в `en.strings` нет ключа `tile_set.custom.label`, **When** UI запрашивает, **Then** UI показывает сам ключ `tile_set.custom.label` (visible debugging, не crash), runtime warning логируется.

---

### User Story 4 — Senior-friendly UI primitives используются consumer'ами (Priority: P1)

Разработчик S-1 импортирует из `core/ui-senior/` крупную кнопку `SeniorButton`, primary tap target — 64dp height (выше senior-safe baseline 56dp). На экране с fontScale = 1.5 (системный large text) кнопка автоматически растёт по высоте, текст не обрезается. Цвет фона — warm-contrast palette (тёплый бежевый фон / тёмный текст), контраст ≥ 7:1 (WCAG AAA).

**Why this priority**: senior-safe UI Kit — universal foundation для всей экосистемы (launcher + future messenger + future album). Если первая app-family строит свои примитивы вместо использования `core/ui-senior/`, дисциплина рушится с дня 1.

**Independent Test**: в demo Compose Preview отрисовать `SeniorButton("Текст")`, измерить height = 64dp; повторить с `fontScale = 2.0`, проверить height ≥ 96dp (растёт пропорционально); проверить tap target через accessibility scanner ≥ 56dp baseline.

**Acceptance Scenarios**:

1. **Given** `SeniorButton` отрисован с default параметрами, **When** measure, **Then** tap target height ≥ 56dp на fontScale=1.0, контраст label vs background ≥ 7:1.
2. **Given** `SeniorButton` с очень длинным текстом, **When** отрисован на узком экране, **Then** текст переносится на 2 строки (не обрезается), кнопка адаптирует высоту.
3. **Given** `SeniorWarmTheme.Light` применён в Compose, **When** TalkBack читает любую кнопку из `core/ui-senior/`, **Then** `contentDescription` присутствует (либо явный, либо derived из label).
4. **Given** разработчик S-1 импортирует Composable из `core/ui-senior/`, **When** билдится `:app:assembleDebug`, **Then** build проходит (модуль доступен как dependency).

---

### User Story 5 — Lint rule блокирует launcher-specific код в core/* (Priority: P1)

Разработчик случайно добавляет в `core/wizard/` импорт `import com.eastclinic.app.home.TileGrid` (нарушение launcher-agnostic дисциплины). При `./gradlew :core:wizard:check` CI fails с понятным сообщением: «Module core/wizard imports app/* class TileGrid — core modules MUST stay launcher-agnostic (see CLAUDE.md rule 7 + glossary §7a). If TileGrid is generic enough, move it to core/ui-senior/.»

**Why this priority**: это **load-bearing** fitness function. Без неё reusability discipline §7a — bestpractice без enforcement, и через 3 месяца модули зарастут launcher-specific импортами, extraction станет невозможным без переписывания.

**Independent Test**: написать тестовый файл `core/wizard/src/commonMain/kotlin/test/BadImport.kt` с `import com.eastclinic.app.foo.Bar`, прогнать lint task, проверить что (a) build fails, (b) сообщение объясняет нарушение и указывает путь fix'а, (c) удалить файл → build снова green.

**Acceptance Scenarios**:

1. **Given** новый Kotlin файл в `core/wizard/` импортирует класс из `app/`, **When** CI прогоняет lint task, **Then** task fails с сообщением, идентифицирующим (a) импортирующий файл, (b) импортируемый класс, (c) ссылку на CLAUDE.md rule 7.
2. **Given** новый Kotlin файл в `core/localization/` или `core/ui-senior/` импортирует из `app/`, **When** lint, **Then** аналогично fails (правило применяется ко всем трём `core/*` модулям).
3. **Given** новый Kotlin файл в `app/` импортирует из `core/wizard/`, **When** lint, **Then** **passes** (правило направленное: `core/` не зависит от `app/`, обратное разрешено).

---

### User Story 6 — Forward-compat read неизвестных полей в bundled JSON (Priority: P2)

S-1 поставляет `tile.set` JSON, в котором появилось дополнительное опциональное поле `body.tiles[].colorHint` (добавлено в новой версии S-1, но F-3 reader о нём не знает). `BundledConfigSource` читает JSON без warning'а / crash'а; unknown поле игнорируется, остальные поля парсятся нормально. App запускается, плитки рисуются (без colorHint, но с position / actionType / labelKey).

**Why this priority**: forward-compat readers — основа аддитивного развития wire-format (glossary §4.3 + CLAUDE.md rule 5). Без US-6 любое добавление поля требует bump `schemaVersion`, что ломает старые версии app — антипаттерн Kubernetes-style.

**Independent Test**: положить в `commonMain/resources/test-fixtures/` файл `tile-set-with-future-fields.json` (содержит `body.tiles[].futureField`), прогнать `BundledConfigSource.load("tile-set-with-future-fields")`, проверить что (a) возвращается валидный `TileSet`, (b) поля `position/actionType/labelKey/iconKey` присутствуют, (c) unknown поле молча проигнорировано (нет exception, нет log error).

**Acceptance Scenarios**:

1. **Given** JSON с `schemaVersion: 1` (known), unknown additive поле в `body`, **When** parsed, **Then** успешно возвращается valid object, unknown поле dropped.
2. **Given** JSON с `schemaVersion: 999` (unknown future major), **When** parsed, **Then** возвращается `ConfigSourceResult.IncompatibleVersion(found=999, known=1)`, UI показывает hard-fail dialog «версия конфига несовместима — обновите приложение».
3. **Given** JSON с rename'нутым required полем (например, `tiles` → `slots`), **When** parsed, **Then** возвращается `ConfigSourceResult.InvalidShape(reason)`, UI показывает diagnostic для разработчика (этот сценарий не должен происходить в bundled, но reader защищён).

---

### User Story 7 — Wizard показывает hint поверх UI (Priority: P3)

После завершения wizard'а, при первом открытии главного экрана, `TutorialHintManager` показывает overlay-подсказку «Вы можете долго нажать на плитку чтобы её переставить» рядом с первой плиткой. Подсказка имеет кнопку «Понял» — нажатие dismisses подсказку навсегда (persistent flag).

**Why this priority**: TutorialHintManager — поглощённая FUTURE-SPEC-006 (onboarding-and-tutorials). В F-3 даём **runtime hint primitive** + persistent dismissed state. Конкретные hint data (тексты, anchors) — **hardcoded** в F-3 фикстурах для тестов; реальные hints — в S-1.

**Independent Test**: показать `TutorialHintManager.show(hintId="test-hint-1", anchor=TopLeft, text="Test hint")`, проверить (a) overlay появился, (b) tap на «Понял» → overlay исчезает, (c) повторный вызов `show("test-hint-1")` — overlay **не** появляется (already dismissed).

**Acceptance Scenarios**:

1. **Given** hint `test-hint-1` ни разу не dismissed, **When** `TutorialHintManager.show("test-hint-1", …)`, **Then** overlay появляется поверх UI с текстом из anchor.
2. **Given** hint `test-hint-1` dismissed ранее, **When** `show("test-hint-1", …)`, **Then** overlay **не** появляется (persistent state respected).
3. **Given** разные hint id, **When** `show("test-hint-2", …)`, **Then** overlay появляется (dismissed state per-hint, не global).

---

### Edge Cases

- **Пустой `wizard.manifest.body.steps[]`** → WizardEngine завершается немедленно с `WizardOutcome.Completed(initialConfig = DefaultEmptyConfig)`. Не crashes. Use case: app-family у которой first-run = no-op (например, диагностическая утилита).
- **Step с unknown `stepType`** (не в реестре зарегистрированных) → WizardEngine **пропускает** этот шаг с warning'ом в diagnostic log (forward-compat для будущих step types из новых версий manifest'а). Альтернатива hard-fail отвергнута: ломает приложение из-за step type, который, возможно, опциональный.
- **`tile.set` ссылается на `iconKey`, которого нет в ресурсах** → UI показывает default placeholder icon (нет crash). Diagnostic warning.
- **Locale изменилась во время работы wizard'а** (пользователь зашёл в системные настройки, поменял язык, вернулся) → wizard перерисовывается на новом языке; ответы предыдущих шагов сохранены (они хранятся как **ключи** / **enum**, не литералы).
- **JSON parsing exception** (corrupted bundled — теоретически не возможно, но защита) → `BundledConfigSource` возвращает `ConfigSourceResult.ParseError(reason)`, app показывает hard-fail screen «не удалось прочитать конфигурацию — переустановите приложение».
- **fontScale = 0.85 (минимальный) или 2.0 (максимальный)** → `SeniorButton` и другие UI Kit примитивы корректно масштабируются; baseline 56dp остаётся минимальной нижней границей (на fontScale=0.85 кнопка может быть выше 56dp, не ниже).
- **Process death во время самого первого шага wizard'а (до первого checkpoint write)** → wizard стартует с шага 0 (нет checkpoint to resume). Это **acceptable** — пользователь не успел ответить ни на один шаг, повторное начало не frustrating.
- **System locale — не в supported 10** (например, `ko-KR`) → fallback на EN с дня 1. Diagnostic note добавлен в analytics (не PII, только locale code) для product team — узнать какие языки запрашиваются.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Part A — `core/wizard/` module: WizardEngine

- **FR-001** *(REVISED 2026-06-17)*: System MUST добавить **пакеты внутри существующего `:core` KMP-модуля** (НЕ создавать новый module). Пакеты:
  - `com.launcher.api.wizard` (commonMain) — domain ports: `WizardEngine`, `WizardStep`, `ConfigSource`, `WizardCheckpointStore`, `DismissedHintsStore`, `UserPreferencesStore`, `SystemSettingPort`, `Clock`, `AnimationPreferenceProvider`, `DiagnosticEmitter`, `PermissionRequestPort`.
  - `com.launcher.api.localization` (commonMain) — `StringResolver`, `LocaleProvider`, `RtlHelper`.
  - `com.launcher.api.wizard.data` (commonMain) — wire format types (WizardManifest, etc.).
  - `com.launcher.adapters.wizard` (androidMain + iosMain) — реальные адаптеры (PersistentCheckpointStore, AndroidSystemSettingAdapter, etc.).
  - `com.launcher.ui.senior` (commonMain) — Compose Multiplatform UI primitives (SeniorButton, SeniorWarmTheme, etc.).
  - `com.launcher.ui.wizard` (commonMain) — WizardHost Composable + step Composables (UIChoiceStep, SystemSettingStep, TutorialHintStep), используют Decompose `Stack<Configuration>`.

  Пакетная структура consistent с existing convention (`api/setup` от [spec 010](../010-setup-assistant/), `api/action` от [spec 005](../005-action-architecture-v2/), `adapters/config` от [spec 008](../008-bidirectional-config-sync/)). iOS support получается **автоматически** через CMP (per [ADR-005](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md)) — отдельные iosMain implementations пишутся для platform-specific adapters только.
- **FR-002**: `core/wizard/` MUST экспонировать port `WizardEngine` в domain layer со следующим контрактом: `suspend fun run(manifest: WizardManifest): WizardOutcome` + `fun currentState(): StateFlow<WizardState>`.
- **FR-003**: `WizardEngine` MUST реализовать persistent checkpoint после **каждого** успешно завершённого шага (write to `WizardCheckpointStore` port). Checkpoint содержит: `schemaVersion: Int = 1` (wire format version per CLAUDE.md rule 5), `manifestId: String`, `currentStepIndex: Int`, `answers: Map<StepId, JsonElement>`. При load checkpoint'а с `schemaVersion > known` — engine считает checkpoint invalid → starts wizard from step 0 (graceful, не crash; редко-возможный случай app downgrade).
- **FR-003a (in-progress answer policy)**: In-progress answer на **текущем** шаге (пользователь сделал выбор, но кнопку «Далее» не нажал) MUST переживать Activity recreation (rotation, theme change, language change) через Compose `rememberSaveable` на уровне step Composable. Применимо ко всем bundled steps F-3 (LanguageStep, ThemeStep, TextSizeStep, GridSelectionStep, ScreenLayoutPickerStep, TileSetPickerStep) — все имеют single-choice answer, тривиально serializable. После нажатия «Далее» answer commit'ится в `WizardCheckpoint` (FR-003). Custom steps от app-family должны следовать тому же паттерну.
- **FR-004**: При старте `WizardEngine.run(manifest)`, если существует checkpoint для `manifest.id`, engine MUST резюмировать с `currentStepIndex` из checkpoint'а, восстановив `answers`.
- **FR-005**: После `WizardOutcome.Completed`, `WizardEngine` MUST установить persistent флаг `wizardCompleted(appFamilyId)`. При следующем запуске app-family с тем же ID wizard **не** показывается повторно (бабушка не должна проходить настройку дважды).
- **FR-006**: `WizardCheckpointStore` MUST быть port (interface) в domain; реальная имплементация (`PersistentCheckpointStore` через DataStore/SharedPreferences) — в `:app/androidMain`; `InMemoryCheckpointStore` — в `core/wizard/` для тестов.

#### Part B — `core/wizard/` module: WizardStep library

- **FR-007**: `core/wizard/` MUST содержать публичный interface `WizardStep` со следующими методами: `val stepType: StepType`, `suspend fun render(params: StepParams): StepResult`, `val canSkip: Boolean`, `val canGoBack: Boolean`.
- **FR-008** *(consolidated per 2026-06-16)*: `core/wizard/` MUST поставлять **три** реализации `WizardStep`, переиспользуемые между app-family. Каждая консолидирует ранее существовавшие подтипы в один generic step с параметром-id, который указывает на entry в соответствующем pool JSON:
  - **`UIChoiceStep(optionId: String)`** *(новый, заменяет LanguageStep / ThemeStep / TextSizeStep / GridSelectionStep / ScreenLayoutPickerStep / TileSetPickerStep)* — обобщённый шаг для UI / UX выбора. Параметр — `optionId` ссылается на entry в `ui-customization.pool` (новая 5-я JSON-схема, см. Part K). Поведение определяется entry: simple-choice (radio buttons), pick-from-bundled (загружает доступные `screen.layout` / `tile.set` через ConfigSource), и т.д. Записывает результат в `UserPreferencesStore` (для language/theme/fontScale/grid) или в initial `ConfigDocument` (для tileSet/screenLayout).
  - **`SystemSettingStep(settingId: String)`** — обобщённый шаг для применения **любой** Android system setting (per C-18). Параметр — `settingId` ссылается на entry в `system-settings.pool`. Поведение шага определяется `mechanism` из pool entry: для `StandardPermission` — runtime permission request; для `SpecialPermission` / `AccessibilityService` / `DeepLink` — deep-link на Settings экран; для `InAppOnly` — сразу применяется внутри app. Detection после возврата пользователя — per `detectionStrategy` (Programmatic check / SelfAttest user button / Indeterminate fallback to SelfAttest). См. Part K.
  - **`TutorialHintStep(hintId: String)`** — показывает hint overlay, ждёт dismiss. Hint data hardcoded в Phase 1 фикстурах (per FR-025).

  **Удалено / поглощено**:
  - `PairingStep` — НЕ ships в F-3. S-2 при материализации pairing добавит `PairingStep` через DI Map (FR-009) — F-3 не резервирует имя stub'ом (Q-C confirmed (c) 2026-06-16).
  - `LanguageStep`, `ThemeStep`, `TextSizeStep`, `GridSelectionStep`, `ScreenLayoutPickerStep`, `TileSetPickerStep` — поглощены в `UIChoiceStep(optionId)`. Опции описаны в `ui-customization.pool` JSON, не в Kotlin. Это даёт **симметрию с `system-settings.pool`** и упрощает будущее расширение (новая UI опция = новая entry в pool, не новый Kotlin class).
- **FR-008a (SystemSettingStep denial behaviour)**: При denial пользователем системного permission/setting (return value `SettingStatus.NotApplied`), `SystemSettingStep` MUST:
  - (a) показать **rationale screen** с пояснением почему setting нужен (текст из pool entry's `extendedInstructionKey` или дефолтного `descriptionKey`);
  - (b) предоставить **две кнопки**: «Попробовать снова» (re-trigger `SystemSettingPort.applyOrPrompt`) и либо «Пропустить» (если pool entry имеет `canSkip = true`), либо «Назад» (если `canSkip = false`);
  - (c) при permanent denial (Android: `shouldShowRequestPermissionRationale = false` для StandardPermission mechanism) — заменить «Попробовать снова» на **«Открыть настройки приложения»** (deep-link на `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`);
  - (d) emit'нуть `wizardStepDenied(settingId, isPermanent)` через `DiagnosticEmitter`.
  Применяется ко всем `SettingMechanism` кроме `InAppOnly` (там denial невозможен — наш app сам управляет).
- **FR-008b (state change announcements для accessibility)**: При transition между wizard шагами, `WizardEngine` MUST emit accessibility event через Compose `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`: «Шаг N из M» (например «Шаг 3 из 5»). TalkBack announce'ит progress без необходимости пользователю swipe'ать к progress indicator. Аналогично для:
  - Step submission success: «Шаг N завершён, переход к следующему»
  - Self-attest result: «Настройка подтверждена» / «Настройка не подтверждена»
  - Hard-fail dialog: «Ошибка: версия конфигурации несовместима»
  - Wizard completion: «Настройка завершена»

  Все announcement strings проходят через `StringResolver` (локализованы во все 11 языков per FR-031). Это критично для blind senior users, которые не видят visual progress indicators.
- **FR-008c (visual progress indicator)**: Каждый wizard step screen MUST содержать visual progress indicator **над content**: текст «Шаг N из M» (≥ 18sp) + visual dots (filled = completed, current step highlighted, remaining = empty) ИЛИ progress bar. Indicator updates после каждого completed / skipped шага. Применяется ко всем app-families (Simple Launcher S-1, Admin App S-2, future TV / Caregiver). Visual + audio (FR-008b) complement друг друга: TalkBack users получают audio announcements, sighted elderly users видят visual progress (важно — senior users часто **не помнят**, сколько шагов осталось).
- **FR-008d (System Back behaviour)**: System hardware/gesture Back button во время wizard'а MUST вести себя identично к «Назад» button (FR-007 `canGoBack`):
  - На шаге `N > 0` → возврат к шагу `N - 1` (preserve answers per FR-003a + checkpoint FR-003).
  - На шаге `0` → **no-op** (НЕ exit wizard, НЕ restart). Display brief toast: «Чтобы выйти, закройте приложение». User должен explicitly kill app для exit (design choice — first-run wizard mandatory before launcher функционал available). Senior expectation match: Back = previous step, никогда unexpected wizard exit.
- **FR-009**: `WizardEngine` конструируется через DI с параметром `steps: Map<StepType, WizardStep>`. Bundled step implementations (FR-008) регистрируются на уровне app DI module как обычная Map composition. Custom step types от будущих app-family — additive: app's DI module добавляет entry в ту же Map. Никакого отдельного `Registry` класса не вводится в F-3 (rule 4 MVA — нет current consumer'а custom step type'ов).
- **FR-010**: Unknown `stepType` в `wizard.manifest.body.steps[]` (отсутствует в registry) MUST **пропускаться** с diagnostic warning, не crashes. (Forward-compat: новые app-family могут декларировать шаги, которых нет в core/wizard/.)

#### Part C — `core/wizard/` module: Пять bundled JSON-схем

- **FR-011**: System MUST определить **пять независимых** wire-format JSON-схем (`wizard.manifest`, `screen.layout`, `tile.set`, `system-settings.pool`, `ui-customization.pool`) со следующим общим 6-полевым header'ом (glossary §4.1):
  ```json
  {
    "schemaVersion": <int>,
    "id": "<kind>.<slug>",
    "name": "<localization-key>",
    "description": "<localization-key>",
    "deviceClass": ["android-phone" | "android-tv" | "*"],
    "body": { /* специфика kind'а */ }
  }
  ```
- **FR-012**: Схема **`wizard.manifest`** (`schemaVersion: 1`) MUST содержать в `body`: `appFamilyId: String` + `steps: List<StepEntry>`, где `StepEntry = { stepType: String, params: Map<String, JsonElement>, canSkip: Boolean? = false }`. Per C-12: `stepType` — sealed class enum (compile-time проверка известных типов); `params` — open `Map<String, JsonElement>` (forward-compat без bump `schemaVersion` при добавлении нового step type).
- **FR-013**: Схема **`screen.layout`** (`schemaVersion: 1`) MUST содержать в `body`: `gridRows: Int`, `gridCols: Int`, опциональные `bottomToolbar: ToolbarSpec?`, `topTabs: List<TabSpec>?`. **НЕ** содержит данные плиток.
- **FR-014**: Схема **`tile.set`** (`schemaVersion: 1`) MUST содержать в `body`: `tiles: List<TileSpec>`, где `TileSpec = { position: GridPosition, actionType: String, labelKey: String, iconKey: String }`. `position` — `{ row, col }`. `actionType` — opaque string-reference к capability registry (спека 005). **F-3 НЕ validates `actionType` semantically и НЕ обрабатывает tap behavior** — это S-1 (Simple Launcher) + F-2 (capability registry runtime) territory. F-3 only поставляет schema + parsing.
- **FR-014a (`ui-customization.pool` — новая 5-я схема, parallel structure to `system-settings.pool`)**: Схема **`ui-customization.pool`** (`schemaVersion: 1`) MUST содержать в `body`:
  ```json
  {
    "platform": "*",
    "options": [
      {
        "id": "<option-id, slug>",          // e.g. "language", "theme", "fontScale", "grid", "screenLayout", "tileSet"
        "kind": "simple-choice" | "pick-from-bundled",
        "questionKey": "<localization-key>", // вопрос-текст («Какой язык?»)
        "descriptionKey": "<localization-key, optional>",
        "criticality": "Required" | "Optional",
        "defaultValue": "<value>",
        // для simple-choice — inline choices:
        "choices": [{ "value": "ru", "labelKey": "ui.lang.ru" }, ...]
        // для pick-from-bundled — reference на другую JSON kind:
        "choicesFrom": { "kind": "tile.set" | "screen.layout", "filter": <optional> }
      }
    ]
  }
  ```
  F-3 поставляет **один** bundled pool `core/wizard/src/commonMain/resources/ui-customization/ui-pool.json` со следующим минимальным набором (для S-1 wizard'а):
  - `language` (simple-choice, Required, default = system locale, choices = 11 поддерживаемых языков FR-030)
  - `theme` (simple-choice, Optional, default = `auto`, choices = `light` / `dark` / `auto`)
  - `fontScale` (simple-choice, Optional, default = system, choices = `1.0` / `1.3` / `1.6`)
  - `grid` (simple-choice, Optional, default = `3×4`, choices = `2×3` / `3×4` / `4×5`)
  - `screenLayout` (pick-from-bundled, Optional, choicesFrom = bundled `screen.layout` JSONs)
  - `tileSet` (pick-from-bundled, Required, choicesFrom = bundled `tile.set` JSONs)
- **FR-014b (delta-wizard support — per Q-2.1)**: `WizardEngine` MUST экспонировать API:
  ```kotlin
  fun diffPending(savedCompletedManifest: WizardManifest?, currentManifest: WizardManifest): List<PendingStep>
  data class PendingStep(val stepEntry: StepEntry, val criticality: Criticality)
  enum class Criticality { Required, Optional }
  ```
  Logic: comparing `wizardCompleted` snapshot (manifest steps пройденные при v1) с current manifest. Возвращает entries из current, отсутствующие в snapshot — это «новые шаги после обновления app». Criticality берётся либо из явного `criticality` поля в step entry, либо из referenced pool entry. Этот API потребляется S-1 (banner UI «новая настройка») и спекой 008 (push admin'у на paired устройство).
- **FR-014c (auto-order поддержка)**: `wizard.manifest` body MAY содержать `autoOrder: Boolean = false`. Если `true`, `steps[]` массив игнорируется, engine **автоматически** строит порядок: все Required entries из обоих pools (system-settings + ui-customization) первыми, Optional — после. Это разгружает Simple Launcher manifest (S-1 manifest = `{appFamilyId, autoOrder: true}` — лаконичный). Если `false` или поле отсутствует — manifest **должен** явно перечислить `steps[]` (admin app может хотеть кастомный порядок).
- **FR-015**: Все **пять** схем MUST поддерживать **forward-compatible read**: unknown additive поля в `body` молча игнорируются reader'ом. Reader использует Kotlin Serialization с `ignoreUnknownKeys = true`.
- **FR-016**: Все **пять** схем MUST поддерживать **hard-fail** на `schemaVersion > known`: reader возвращает `ConfigSourceResult.IncompatibleVersion`, UI MUST показать **non-blocking fallback screen** (per Q-6 confirmed (b)): «Обновите приложение в Play Маркет» + одна большая кнопка [Открыть Play Маркет]. App **остаётся открытым** (НЕ `finishAffinity()`). Home button → бабушка возвращается к её предыдущему launcher'у (Android system fallback). Это безопаснее silent exit'а.
- **FR-017**: System MUST содержать roundtrip-тест **для каждой** из пяти JSON-схем (`wizard.manifest`, `screen.layout`, `tile.set`, `system-settings.pool`, `ui-customization.pool`): serialize fixture → deserialize → assert structural equality + assert localization keys (`name`, `description`, `labelKey`, `iconKey`, `questionKey`) **не литералы**. Дополнительно — roundtrip для persistent formats `WizardCheckpoint` и `UserPreferences` (in-memory store → write → read → assertEquals).
- **FR-018**: System MUST содержать backward-compat reader test: положить fixture с `schemaVersion: 1` + minimal valid body, и убедиться что reader будущей версии всё ещё читает его (placeholder — пока единственная version, тест валидирует механизм).

#### Part D — `core/wizard/` module: ConfigSource adapter pattern

- **FR-019**: System MUST определить port `ConfigSource` в `core/wizard/` domain layer:
  ```kotlin
  interface ConfigSource {
    suspend fun list(kind: ConfigKind): List<ConfigSummary>
    suspend fun load(kind: ConfigKind, id: String): ConfigSourceResult
  }
  enum class ConfigKind { WizardManifest, ScreenLayout, TileSet }
  sealed class ConfigSourceResult {
    data class Success(val document: ConfigDocument): ConfigSourceResult()
    data class IncompatibleVersion(val found: Int, val known: Int): ConfigSourceResult()
    data class ParseError(val reason: String): ConfigSourceResult()
    data class NotFound(val id: String): ConfigSourceResult()
  }
  ```
- **FR-020**: System MUST поставить **одну** реальную имплементацию `BundledConfigSource`. Bundled JSON-файлы физически лежат в `core/wizard/src/commonMain/resources/` (читаются через moko-resources cross-platform API — это позволит JVM unit-тестам прогонять JSON без эмулятора + готовит к будущему iosMain без рефакторинга путей).
- **FR-021**: `BundledConfigSource` MUST содержать inline-TODO (per memory `feedback_exit_ramps_as_todos.md`):
  ```kotlin
  // TODO(shareability): same JSON format will be served by future ConfigSource adapters
  // without rewriting BundledConfigSource. Format must NOT change for these to plug in;
  // only the source changes.
  //   - FileConfigSource     — share intent / import from file picker
  //   - NetworkConfigSource  — server-curated catalog (Phase 4+, см. docs/dev/server-roadmap.md)
  //   - MarketplaceConfigSource — community gallery (Phase 5+)
  // Identity-bound fields stay forbidden in shareable format (CLAUDE.md rule 9).
  ```
- **FR-022**: System MUST поставить `FakeConfigSource` в `core/wizard/src/commonTest/` — in-memory имплементация, конструируется из `Map<ConfigKind, Map<String, ConfigDocument>>`. Используется во всех wizard unit-тестах.

#### Part E — `core/wizard/` module: TutorialHintManager

- **FR-023**: System MUST содержать `TutorialHintManager` с API: `suspend fun show(hintId: String, anchor: HintAnchor, textKey: String): HintResult` + `fun isDismissed(hintId: String): Boolean` + `suspend fun reset(hintId: String)`.
- **FR-024**: `TutorialHintManager` MUST использовать persistent storage (`DismissedHintsStore` port, реализация — DataStore/SharedPreferences в `:app/androidMain`), чтобы dismissed hints не показывались повторно.
- **FR-025**: В F-3 hint data **hardcoded** в фикстурах для тестов; конкретные hint texts/anchors для S-1 — поставляются в S-1. Inline TODO: `// TODO: future hint.set schema когда появится первый реальный hint в S-1+ (glossary §3 «hint.set»).`

#### Part F — `core/localization/` module

- **FR-026** *(REVISED 2026-06-17)*: Localization API живёт в **пакете `com.launcher.api.localization`** в существующем `:core` модуле (commonMain). НЕ отдельный модуль (per C-7). Использует Compose Multiplatform Resources (`compose.components.resources`) — уже подключено в `core/build.gradle.kts`.
- **FR-027**: `core/localization/` MUST экспонировать `StringResolver` port: `fun resolve(key: String, args: Map<String, Any> = emptyMap()): String` + `fun currentLocaleTag(): String` (BCP-47 tag, например `"ru"`, `"kk-Latn"`, `"en-US"`). Domain представление locale — **BCP-47 String**, consistent с `UserPreferences.languageOverride: String?`. Это избегает зависимости domain layer от `java.util.Locale` (JVM-only) и даёт future iosMain readiness без рефакторинга signatures.
- **FR-028**: `StringResolver` MUST детектировать system locale автоматически (Android: `Resources.configuration.locales[0]` через `LocaleProvider` port, реализация в `:app/androidMain`). **НЕТ** app-level переключателя локали в F-3 (per glossary contract; future override через `LanguageStep` записывает в persistent override в `UserPreferencesStore` — см. Part I — читаемый `StringResolver`'ом).
- **FR-029**: `StringResolver` MUST реализовать fallback chain: **запрошенная локаль → EN (base) → key as literal**. Каждый fallback порождает diagnostic warning (level `warn` для EN-fallback, level `error` для key-as-literal fallback). Per C-6: EN — single source of truth, всегда содержит каждый key (защищено CI fitness function FR-031).
- **FR-030**: Supported languages (per C-6 + C-9): **base language — EN** (single source of truth). 10 переводов: **RU, ES, ZH, AR, HI, PT, DE, FR, JA, kk-Latn** (казахский латиница — future-proof per Казахстан переход на латиницу к 2031; см. A-15a). Итого 11 локалей.
- **FR-031**: System MUST содержать **CI fitness function** (`./gradlew :core:localization:checkTranslations`) который **fails build** если: (a) string key присутствует в `en/strings.xml` (base), но отсутствует в любом из 10 не-base языковых файлов; (b) string key используется в коде/JSON, но отсутствует в `en/strings.xml` (нет base value — нельзя перевести); (c) orphan key (есть в `<lang>/strings.xml`, не используется в коде) — warning, не fail. Per C-15: реализация через **Konsist** (JUnit-style test, runs as part of `./gradlew check`).
- **FR-031a (translation pipeline)** — per C-10: System MUST содержать skill `procedure-translate-spec-strings` (в `.claude/skills/`), который запускается **в конце** `speckit-tasks` orchestrator (async, после генерации tasks.md и cross-artifact-trace). Skill: (a) делает `git diff` против HEAD для `core/localization/src/commonMain/resources/MR/base/strings.xml`; (b) для каждого нового / изменённого key читает `CONTEXT.json` (mandatory — если context отсутствует, skill fails с инструкцией добавить); (c) читает `GLOSSARY.md` для canonical терминов; (d) генерирует переводы для 10 не-base языков через Claude API; (e) записывает переводы в `<lang>/strings.xml`; (f) запускает FR-031 fitness function для verification; (g) git-stage'ит изменённые файлы. Skill читает Claude API key из `ANTHROPIC_API_KEY` env var; setup instructions для нового developer'а — в `core/localization/README.md` (раздел «Translation pipeline setup»). Без key skill terminates с понятным сообщением: «`ANTHROPIC_API_KEY` not set — see `core/localization/README.md`».
- **FR-031b (CONTEXT.json structure)**: System MUST содержать `core/localization/strings-context/CONTEXT.json` со следующей schema:
  ```json
  {
    "schemaVersion": 1,
    "entries": {
      "wizard.next_button": {
        "value": "Next",
        "context": "Button on bottom of wizard step screen. Tap → moves to next step. Senior-friendly: ≥56dp tap target, primary color. Audience: elderly user, tone should be encouraging not technical.",
        "screenshot": "docs/screenshots/wizard-step-bottom.png"
      }
    }
  }
  ```
  Поле `context` — **mandatory** для каждого нового key (без него FR-031a skill отказывается генерировать переводы). Поле `screenshot` — опциональное; если присутствует, Claude (multimodal) читает изображение для визуального контекста перевода. Translation skill валидирует `schemaVersion` matches expected; mismatch → skill fails с сообщением «`CONTEXT.json` schemaVersion mismatch — update translation skill or revert».
- **FR-031c (GLOSSARY.md)**: System MUST содержать `core/localization/GLOSSARY.md` — canonical терминология проекта с переводами на все 10 не-base языков. Translator (Claude) обязан использовать **точно эти** переводы для перечисленных терминов, не альтернативы (защита от drift между релизами). Минимальный начальный set: `Tile`, `Wizard`, `Admin`, `Managed`, `Senior`. Plus tone guidelines per language (formal/informal address, register).
- **FR-031d (translation memory)**: Translation memory = git history файлов `<lang>/strings.xml`. Никакого внешнего TM-провайдера, никакого external database. Существующие переводы регенерируются **только** если изменился base value (защита от drift).
- **FR-031e (plural support)**: Count-dependent strings MUST использовать moko-resources `plurals` resource (ICU plural rules) вместо string concatenation. Например, «Шаг 3 из 5» (FR-008b/c) реализуется как:
  ```xml
  <plural name="wizard_step_n_of_m">
    <item quantity="one">Шаг %1$d из %2$d</item>
    <item quantity="few">Шаг %1$d из %2$d</item>
    <item quantity="many">Шаг %1$d из %2$d</item>
    <item quantity="other">Шаг %1$d из %2$d</item>
  </plural>
  ```
  CI fitness function FR-031 проверяет, что для каждого plural key все 11 локалей имеют все необходимые plural categories (RU: one/few/many/other; AR: zero/one/two/few/many/other; EN/DE/ES: one/other; PT/FR: one/many/other; ZH/JA/KK: other). Translation skill `procedure-translate-spec-strings` (FR-031a) генерирует все plural forms через Claude API с контекстом «grammatical plural form variant».
- **FR-032**: `core/localization/` MUST поставлять RTL detection helper: `fun layoutDirectionFor(localeTag: String): LayoutDirection` (принимает BCP-47 tag; returns RTL для AR/HI tags, LTR для остальных).

#### Part G — `core/ui-senior/` module

- **FR-033** *(REVISED 2026-06-17)*: Senior UI primitives живут в **пакете `com.launcher.ui.senior`** в `:core` commonMain (Compose Multiplatform per [ADR-005](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md)). НЕ отдельный модуль, НЕ Android-only. iOS support получается автоматически через CMP. Wizard host (`com.launcher.ui.wizard`) использует Decompose для navigation. Primitives используют Material 3 (`compose.material3` уже подключен) как foundation с senior-safe overrides.
- **FR-034**: `core/ui-senior/` MUST экспонировать senior-friendly UI primitives:
  - `SeniorButton` — baseline tap target ≥ 56dp height (project senior-safe override от WCAG 48dp), button text baseline ≥ 18sp, `wrapContentWidth()` + `wrapContentHeight()` (adapt к translated label length — RU/DE ~30-40% длиннее EN — и fontScale growth). Inter-element spacing ≥ 16dp. Directional icons в `start` / `end` slot MUST use `autoMirrored = true` для RTL auto-mirror в AR/HI locales.
  - `SeniorIconButton` — аналогично, square ≥ 56dp, autoMirrored = true для directional icons.
  - `SeniorTextField` — input с baseline height ≥ 56dp, `wrapContentHeight()`.
  - `SeniorBodyText` — text Composable с baseline fontSize ≥ 18sp, line-height = 1.5× font size (accommodates AR/HI tall glyphs без vertical clipping).
  - `SeniorTitleText` — text Composable с baseline fontSize ≥ 24sp, line-height = 1.5× font size.
<!-- FR-034a (SeniorButton debounce) REMOVED 2026-06-16 — отложено в future UX polish spec.
     Owner decision: debounce — runtime polishing concern, не foundation. Будет в отдельной 
     спеке «UX customization wizard» когда понадобится. F-3 поставляет SeniorButton без 
     debounce by default; consumers (S-1+) могут навесить debounce wrapper при необходимости. -->
- **FR-035**: `core/ui-senior/` MUST экспонировать `SeniorWarmTheme` (Compose `MaterialTheme` wrapper) с `Light` и `Dark` вариантами; warm-contrast palette (тёплый бежевый base / тёмный текст в light; тёплый коричневый base / светлый текст в dark); WCAG AAA contrast ≥ 7:1 для текста vs background.
- **FR-036**: `core/ui-senior/` MUST экспонировать utilities:
  - `rememberFontScaleAware()` — Compose `State<Float>` реагирующий на system fontScale changes.
  - `SeniorContentDescription` — helper для accessibility wrapping (требует non-empty `contentDescription` или явный `Modifier.clearAndSetSemantics`).
- **FR-036a (reduce-motion для vestibular safety)**: `core/ui-senior/` MUST respect `Settings.Global.ANIMATOR_DURATION_SCALE` (system reduce-motion preference): если scale == 0, все transitions (slide, fade, hint overlay reveal, dialog enter/exit) MUST применяться **instantly** (duration = 0). Если scale > 0 — animations используют scale-multiplied duration (Compose `tween` defaults respect scale). Реализация через port `AnimationPreferenceProvider` (commonMain) + Android impl чтения `Settings.Global` в `:app/androidMain`. Non-blocking default: scale = 1.0 если port не wired. Rationale: senior users с vestibular disorders могут страдать от motion sickness при slide/fade animations — system reduce-motion preference должен respect'иться.
- **FR-037**: `core/ui-senior/` MUST **НЕ содержать** launcher-specific Composables (HomeScreen, TileGrid, AdminToolbar — это `app/` territory). Гарантируется Konsist fitness function FR-038 + код-ревью.

#### Part H — Fitness function: launcher-agnostic guard

- **FR-038** *(REVISED 2026-06-17)*: Build MUST содержать **Konsist** fitness function в `core/src/androidUnitTest/` (Konsist JVM-only, per existing pattern в spec 005 §8). Test class `WizardArchitectureTest`: **fails** если любой class в `com.launcher.api.wizard.*`, `com.launcher.api.localization.*`, `com.launcher.ui.senior.*` импортирует из `com.launcher.app.*` или какого-либо вышестоящего layer'а. Per C-15: rationale — structural cleanliness (не extraction prerequisite). Konsist library уже в `libs.versions.toml` — нужно только написать test files.
- **FR-038a (inter-package directionality)** *(REVISED 2026-06-17)*: Konsist check MUST дополнительно verify directional граф **между пакетами внутри `:core`**: `com.launcher.api.wizard` MAY импортировать из `com.launcher.api.localization` (через `StringResolver` port). `com.launcher.api.wizard` MUST NOT импортировать из `com.launcher.ui.senior` или `com.launcher.ui.wizard` (wizard engine — pure logic, UI consumer composes). `com.launcher.ui.senior` MUST NOT импортировать ни `com.launcher.api.wizard`, ни `com.launcher.api.localization` (self-contained UI primitives). `com.launcher.ui.wizard` MAY импортировать api.wizard + ui.senior (host slot).
- **FR-039**: Сообщение об ошибке lint rule MUST содержать: (a) полный путь импортирующего файла, (b) полное имя импортируемого класса, (c) пояснение «core modules — discipline boundary per CLAUDE.md rule 7 + glossary §7a», (d) предложение fix'а («move the class to core/ui-senior/ if generic, or stop importing it if launcher-specific»).
- **FR-040**: Обратное направление (app/* импортирует core/*) MUST остаться **разрешённым** (правило directional: core не зависит от app, не наоборот).
- **FR-041**: Lint rule MUST запускаться в `./gradlew check` (default CI gate), не как opt-in.

#### Part I — UserPreferencesStore (wizard answers persistence)

Per C-5: wizard answers (theme, fontScale, language override) хранятся **локально** в F-3 через отдельный `UserPreferencesStore`. Спека 008 (ConfigDocument) **не трогается** в F-3. Goal state — admin remote-меняет UX preferences бабушки через расширение ConfigDocument — реализуется в отдельной спеке после F-4 (AuthProvider) + cloud sync infrastructure ready.

- **FR-047**: System MUST определить port `UserPreferencesStore` в `core/wizard/` domain layer (commonMain):
  ```kotlin
  interface UserPreferencesStore {
    suspend fun save(prefs: UserPreferences)
    fun observe(): Flow<UserPreferences>
    suspend fun current(): UserPreferences
  }
  data class UserPreferences(
    val schemaVersion: Int = 1,                                // wire format version per CLAUDE.md rule 5
    val theme: ThemeChoice,                                    // Light | Dark | Auto
    val fontScale: Float?,                                     // null = follow system
    val languageOverride: String?,                             // null = follow system locale; else BCP-47 tag (e.g. "ru", "kk-Latn")
    val attestedSettings: Map<String, AttestationRecord>       // per-settingId self-attestation; per C-20 + FR-058
  )
  data class AttestationRecord(val attestedAt: Instant, val value: Boolean)
  ```
  При load `UserPreferences` с `schemaVersion > known` — `UserPreferencesStore` возвращает defaults (graceful migration: wizard повторно проходится, prefs reset; **не** crash).
- **FR-048**: `UserPreferencesStore` MUST иметь две реализации:
  - `InMemoryUserPreferencesStore` в `core/wizard/src/commonTest/` — для unit-тестов.
  - `PersistentUserPreferencesStore` в `:app/androidMain/` — через DataStore (Android-specific).
- **FR-049**: `WizardOutcome.Completed` MUST содержать `userPreferences: UserPreferences` (read-only snapshot). При завершении wizard'а `WizardEngine` сохраняет `UserPreferences` в `UserPreferencesStore` **до** возврата `Completed` (atomic — если save fails, wizard не считается завершённым).
- **FR-050**: При перезапуске app `StringResolver` MUST читать `UserPreferencesStore.current().languageOverride` (если non-null) — это переопределяет system locale. Аналогично `ThemeProvider` и `FontScaleProvider` (в `:app/androidMain`) читают preferences и применяют их при cold start, **до** первого рендера UI.
- **FR-051 (exit ramp inline TODO)**: `UserPreferencesStore` implementation MUST содержать inline-TODO (per memory `feedback_exit_ramps_as_todos.md`):
  ```kotlin
  // TODO(server-roadmap): UserPreferences should migrate to ConfigDocument.userPreferences
  // when F-4 (AuthProvider) + cloud sync infrastructure ready. This will enable:
  //   - admin remote-control over Managed's theme / fontScale / language (per Vision D-X)
  //   - cross-device preference sync for multi-device admin users
  // Migration plan: additive field in spec 008 ConfigDocument schemaVersion bump,
  // one-time copy from local UserPreferencesStore into ConfigDocument on first sync.
  // Until then: local-only is correct (rule 4 MVA — no cloud sync exists in Phase 1).
  //
  // TODO(care-family-space): когда мессенджер / album / другие ecosystem apps появятся 
  // (Phase 4), вынести общие UX preferences (theme, fontScale, language) в shared 
  // ContentProvider — чтобы launcher экспонировал «эй, какой язык у бабушки?» через 
  // Android ContentProvider, а мессенджер мог это прочитать. Сейчас UserPreferences 
  // приватные для launcher'а (rule 4 MVA — нет второго consumer'а). 
  // Migration plan: добавить ContentProvider в :app/androidMain, не trogать port shape.
  ```
  Запись в [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md): «UserPreferences cloud sync — additive в спеке 008 после F-4». Запись в [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md): «UserPreferences ContentProvider для care family ecosystem — спека после Phase 4 мессенджер».

#### Part J — README-level extraction discipline (документация)

- **FR-042**: Каждый из трёх модулей (`core/wizard/`, `core/localization/`, `core/ui-senior/`) MUST содержать `README.md` с **EXTRACT CANDIDATE** разделом (текст из glossary §7a, разделе «Inline TODO»):
  ```
  // EXTRACT CANDIDATE: this module is designed launcher-agnostic.
  // Trigger for extract to shared library: second REAL consumer
  // (ecosystem app actually importing this module, not "planned").
  // Rule of three (Fowler): extract on second use, library on third.
  // Until then: keep API surface narrow and launcher-agnostic.
  //
  // Sister extract candidates: core/wizard/, core/localization/, core/ui-senior/.
  ```

#### Part K — System Settings Registry (четвёртая JSON-схема)

Per C-18..C-23: пул доступных system settings вынесен в отдельную JSON-схему `system-settings.pool`. `SystemSettingStep` (см. FR-008) ссылается на entries из pool по `settingId`. Pool — централизованный реестр, разный per-platform; F-3 поставляет только Android pool.

##### Wire format `system-settings.pool`

- **FR-052 (общий 6-полевой header)**: схема `system-settings.pool` (`schemaVersion: 1`) MUST содержать тот же 6-полевой header что и три другие схемы (glossary §4.1): `schemaVersion`, `id` (формат `system-settings-pool.<platform>`), `name`, `description`, `deviceClass[]`, `body`.
- **FR-053 (body schema)**: `body` MUST содержать перечень доступных system settings; unknown `mechanism` value в pool entry → `AndroidSystemSettingAdapter` возвращает `SettingStatus.NotSupportedOnPlatform` для этого `settingId`; остальные entries обрабатываются нормально (forward-compat для будущих mechanisms добавленных в новых версиях схемы):
  ```json
  {
    "platform": "android" | "ios" | "android-tv",
    "settings": [
      {
        "id": "<setting-id, slug>",
        "mechanism": "StandardPermission" | "SpecialPermission" | "AccessibilityService" | "DeepLink" | "InAppOnly",
        "criticality": "Required" | "Optional",   // per Q-5 + FR-014c — управляет auto-order и delta-wizard banner severity
        "deepLink": "<intent-action or platform-specific descriptor, optional>",
        "androidMinApi": <int, optional>,
        "dependsOn": ["<other setting-id>", ...],
        "detectionStrategy": "Programmatic" | "SelfAttest" | "Indeterminate",
        "canSkip": <boolean, default false>,        // если true — denial flow (FR-008a) показывает «Пропустить»
        "labelKey": "<localization-key>",
        "descriptionKey": "<localization-key>",
        "extendedInstructionKey": "<localization-key, optional, для сложных шагов где нужны step-by-step инструкции>"
      }
    ]
  }
  ```
- **FR-053a**: F-3 MUST поставить **один** bundled pool файл `core/wizard/src/commonMain/resources/system-settings/android-pool.json` со следующим минимальным набором (для последующего использования S-1). Колонка `criticality` определяет порядок при `autoOrder: true` и severity delta-wizard banner'а:
  | id | mechanism | criticality | canSkip | detection |
  |---|---|---|---|---|
  | `android.role.home` | DeepLink (`RoleManager.createRequestRoleIntent(ROLE_HOME)`) | **Required** | true | Programmatic |
  | `android.permission.POST_NOTIFICATIONS` (Android 13+) | StandardPermission | **Required** | false | Programmatic |
  | `android.permission.CALL_PHONE` | StandardPermission | Optional | true | Programmatic |
  | `android.accessibility.our-service` | AccessibilityService → `Settings.ACTION_ACCESSIBILITY_SETTINGS` | Optional | true | Programmatic (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`) |
  | `android.battery.ignore_optimizations` | SpecialPermission → `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional | true | Programmatic (`PowerManager.isIgnoringBatteryOptimizations`) |
  | `android.hide_status_bar` | AccessibilityService; dependsOn `android.accessibility.our-service` | Optional | true | Indeterminate → SelfAttest |
- **FR-053b (inline TODO в pool file)**: `android-pool.json` MUST содержать комментарий-блок (`_comment` field или sibling `.md` doc) с inline TODO:
  ```
  // TODO(extraction-candidate): system-settings pool currently lives in core/wizard/.
  // Extract candidate: core/system-settings/ module when second real consumer materializes
  // (e.g. messenger spec impos imports). Until then keep here — rule 4 MVA.
  //
  // iOS / TV pools: NOT created in F-3 (per C-23). Add ios-pool.json / tv-pool.json in
  // dedicated spec when first iOS / TV launcher consumer materializes.
  ```

##### `SystemSettingPort` domain port

- **FR-054**: System MUST определить port `SystemSettingPort` в `core/wizard/` domain layer (commonMain):
  ```kotlin
  interface SystemSettingPort {
    suspend fun status(settingId: String): SettingStatus
    suspend fun applyOrPrompt(settingId: String): ApplyResult
  }
  sealed class SettingStatus {
    object Applied : SettingStatus()
    object NotApplied : SettingStatus()
    object Indeterminate : SettingStatus()        // нет programmatic API проверки → требуется SelfAttest
    object NotSupportedOnPlatform : SettingStatus()
    data class CheckFailed(val reason: String) : SettingStatus()
  }
  sealed class ApplyResult {
    object Applied : ApplyResult()
    object PromptShown : ApplyResult()           // открыт deep-link / системный диалог; результат проверится через .status() при возврате
    object UnsupportedMechanism : ApplyResult()
    data class Failed(val reason: String) : ApplyResult()
  }
  ```
- **FR-055**: System MUST поставить `AndroidSystemSettingAdapter` в `:app/androidMain` — реализация `SystemSettingPort`. Адаптер:
  - читает `android-pool.json` для метаданных каждого `settingId`;
  - для `StandardPermission` mechanism: `checkSelfPermission` для status; `ActivityResultLauncher` запрос для apply.
  - для `SpecialPermission` / `AccessibilityService` / `DeepLink` mechanism: открывает `Intent` из `deepLink` поля для apply; programmatic check для status где возможно (например, `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`).
  - для `InAppOnly` mechanism: применяется немедленно внутри app (например, регистрирует key event handler для volume keys).
  - для `Indeterminate` detection strategy: всегда возвращает `SettingStatus.Indeterminate` → consumer (wizard) показывает self-attest UI.
- **FR-056**: System MUST поставить `FakeSystemSettingAdapter` в `core/wizard/src/commonTest/` — in-memory имплементация, конструируется из `Map<String, SettingStatus>`. Используется во всех `SystemSettingStep` тестах.
- **FR-057 (AccessibilityService responsibility boundary)**: Per C-21, `AndroidSystemSettingAdapter` MUST уметь **открыть** deep-link на Accessibility Settings экран и **проверить активацию** нашего service (через `ENABLED_ACCESSIBILITY_SERVICES`). **Конкретный AccessibilityService class** (что именно блокировать — шторку, кнопки громкости, и т.д.) — **не F-3 scope**, делается в S-1. Inline-TODO в `AndroidSystemSettingAdapter`:
  ```kotlin
  // TODO(S-1): concrete AccessibilityService class (com.eastclinic.app.accessibility.OurService)
  // is implemented in S-1. F-3 only provides the adapter that can open settings deep-link
  // and check service activation by class name. F-3 does NOT define what the service blocks.
  ```

##### Self-attestation storage

- **FR-058 (per C-20)**: `UserPreferencesStore.UserPreferences` MUST содержать дополнительное поле `attestedSettings: Map<String, AttestationRecord>`, где `AttestationRecord = { attestedAt: Instant, value: Boolean }`. При завершении `SystemSettingStep` с `detectionStrategy = SelfAttest`: если пользователь нажал «Да я сделал» — сохраняется `AttestationRecord(now(), true)`; «Нет» — не сохраняется (шаг считается incomplete).
- **FR-059 (re-check on session start)**: При cold start app, если в `attestedSettings` есть `Indeterminate`-настройки, system MUST показать non-blocking indicator («проверь что [setting label] всё ещё работает») если **косвенная** проверка показывает что feature не работает (например, шторка свайпается → значит accessibility service не блокирует). Конкретная implementation indicator'а — паттерн из спеки 010 `[!] N` баннера (cross-spec reference, не F-3 ownership).

##### Cross-app consideration (per C-22)

- **FR-060**: F-3 design assumes **no cross-app sharing** of system setting state. Каждое app в экосистеме (launcher, future messenger, future album) делает свой `SystemSettingPort.status(...)` call и получает правдивый ответ от Android. Никакой shared ContentProvider / IPC / cloud store **не** требуется. Это **закреплено как design constraint**, не «может быть в будущем» — если будущая фича потребует cross-app coordination (например, «не повторять wizard в messenger если launcher уже прошёл»), это будет **отдельная спека** с собственным sharing mechanism.

### Cross-cutting Requirements

- **FR-043**: Все три модуля MUST соответствовать CLAUDE.md rule 1: domain ports в `commonMain`, без vendor SDK / Android system types в signatures. Resource loading осуществляется через moko-resources API (которая сама — platform-aware ACL для bundled assets / strings); прямые Android API calls (`Context.assets`, `Resources.getString`) запрещены в `core/*` модулях (защищено lint rule FR-038).
- **FR-044**: Все user-facing строки в bundled JSON (`name`, `description`, `labelKey`, `iconKey`) MUST быть **ключами** к `<lang>.strings`, **никогда** литералами. Roundtrip test FR-017 включает assert на это.
- **FR-045**: Wire format version bump (breaking change) MUST требовать написания migration code **до** ship'а bump'а. В F-3 migrations нет (первая версия каждой из четырёх схем); migration mechanism (форма interface, registry или inline switch) определяется в спеке, которая introduce'ит первый bump, не предзакладывается в F-3. Это rule 4 MVA: не строим infrastructure до того, как реально понадобится.
- **FR-046**: `core/wizard/` MUST содержать запись в `docs/dev/server-roadmap.md` про триггер для `NetworkConfigSource`: «активируется, когда появится community-sharing UI в Phase 4+; bundled остаётся как offline fallback навсегда».

### Key Entities

- **`WizardEngine`** (port в `core/wizard/`): state machine, orchestrates `WizardStep` execution per `wizard.manifest`.
- **`WizardManifest`** (data class): in-memory представление одноимённого JSON; содержит `appFamilyId`, `steps: List<StepEntry>`, общий header.
- **`WizardStep`** (interface): один шаг wizard'а; имеет `stepType`, `render()`, метаданные (canSkip / canGoBack).
- **`WizardCheckpointStore`** (port): persistent хранилище checkpoint'ов; реализации — `InMemoryCheckpointStore` (test), `PersistentCheckpointStore` (app).
- **`WizardOutcome`** (sealed): `Completed(initialConfig: ConfigDocument, userPreferences: UserPreferences)`, `Cancelled`, `Failed(reason)`.
- **`UserPreferences`** (data class в `core/wizard/`): `theme: ThemeChoice`, `fontScale: Float?`, `languageOverride: String?`, `attestedSettings: Map<String, AttestationRecord>`. Persisted локально через `UserPreferencesStore` port (см. Part I).
- **`UserPreferencesStore`** (port): persistent storage для user UX preferences; реализации — `InMemoryUserPreferencesStore` (test), `PersistentUserPreferencesStore` (app via DataStore). Inline-TODO про migration в ConfigDocument после F-4.
- **`ScreenLayout`** (data class): in-memory представление `screen.layout` JSON; `gridRows`, `gridCols`, опциональные toolbar / tabs.
- **`TileSet`** (data class): in-memory представление `tile.set` JSON; `tiles: List<TileSpec>` (position + actionType + labelKey + iconKey).
- **`ConfigSource`** (port в `core/wizard/`): загрузчик трёх JSON-схем; одна имплементация `BundledConfigSource`, одна тестовая `FakeConfigSource`.
- **`ConfigSourceResult`** (sealed): `Success`, `IncompatibleVersion`, `ParseError`, `NotFound`.
- **`StringResolver`** (port в `core/localization/`): резолвит string keys по текущей locale с fallback chain.
- **`TutorialHintManager`** (class в `core/wizard/`): runtime hint overlays + persistent dismissed flags.
- **`DismissedHintsStore`** (port): persistent storage для dismissed hint ids.
- **`SeniorButton` / `SeniorWarmTheme` / etc.** (Composable / Theme в `core/ui-senior/`): senior-friendly UI primitives.
- **`SystemSettingPort`** (port в `core/wizard/`): unified abstraction для проверки и применения любой platform system setting (per C-18). Status / ApplyOrPrompt API.
- **`SettingMechanism`** (sealed): `StandardPermission`, `SpecialPermission`, `AccessibilityService`, `DeepLink`, `InAppOnly`. Определяет способ применения конкретного setting.
- **`DetectionStrategy`** (enum): `Programmatic`, `SelfAttest`, `Indeterminate`. Определяет способ проверки применения.
- **`SystemSettingsPool`** (data class): in-memory представление `system-settings.pool` JSON; per-platform реестр доступных settings с метаданными.
- **`AttestationRecord`** (data class в `UserPreferences`): `{ attestedAt: Instant, value: Boolean }`. Per-setting запись self-attestation от пользователя.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `WizardEngine` проходит test-manifest с 5 шагами end-to-end за < 500ms на JVM unit-test (no UI rendering) — гарантирует, что state-machine overhead negligible.
- **SC-001a (wizard cold-start budget)**: `WizardEngine` first-run cold-start (от `Application.onCreate` до первого кадра wizard step 0) ≤ **300ms** на medium-tier эмуляторе (Pixel 5 API 34). Budget covers: `WizardCheckpointStore.load()` (target ≤ 50ms), `ConfigSource.load(WizardManifest, defaultId)` (target ≤ 100ms), `StringResolver` init для current locale (target ≤ 80ms), step 0 Composable first composition (target ≤ 70ms). Measurement: Android Macrobenchmark library со `startup mode = COLD`; results recorded в `perf-checkpoint.md`.
- **SC-002**: 100% bundled JSON fixtures (test fixtures для каждой из **пяти** схем — `wizard.manifest`, `screen.layout`, `tile.set`, `system-settings.pool`, `ui-customization.pool`) проходят roundtrip-тест (serialize → deserialize → struct equal) за один CI run.
- **SC-002a (delta wizard)**: `WizardEngine.diffPending(savedV1Manifest, newV2Manifest)` правильно идентифицирует Required + Optional новые шаги в синтетическом fixture: v1 имеет 5 шагов, v2 добавляет 1 Required + 1 Optional → `diffPending` возвращает оба, маркированные правильной criticality. Verified JVM unit-test.
- **SC-002b (autoOrder)**: `wizard.manifest` с `autoOrder: true` правильно сортирует шаги: все Required entries (из обоих pools) первыми, Optional — после. Verified JVM unit-test.
- **SC-003**: CI fitness function `checkTranslations` fails build, если в любом из 10 non-base languages отсутствует хотя бы один key, присутствующий в `en/strings.xml` (base) — verified синтетическим breaking test'ом в CI.
- **SC-003a**: Translation pipeline skill `procedure-translate-spec-strings` за один прогон (после speckit-tasks) генерирует валидные переводы для всех 10 не-base языков на синтетическом set'е 5 новых keys (с CONTEXT) — verified e2e тестом skill'а. Качество переводов AR/HI/ZH/JA/KK не measured автоматически (требует human review — отложено).
- **SC-004**: CI lint rule `core/* → app/*` import guard fails build при добавлении test-fixture `BadImport.kt` с запрещённым импортом, и сообщение содержит все 4 элемента из FR-039 — verified в CI.
- **SC-005**: Process death во время wizard'а после шага 3 → перезапуск → resumed на шаге 4 в 100% эмулятор-тестов (Pixel 5 API 34) — verified e2e тестом с `am kill` + relaunch.
- **SC-005a**: Locale change во время wizard'а на шаге 3 → wizard перерисовывается на новом языке за < 500ms, ответы шагов 1-2 сохранены в `WizardCheckpoint`, in-progress answer на шаге 3 сохранён через `rememberSaveable` (per FR-003a) — verified instrumented test (изменить system locale через `LocaleList.setDefault()` + recreate Activity).
- **SC-006**: На fontScale = 2.0 (max system text size) все примитивы `core/ui-senior/` рендерятся без обрезки текста, без overlap'ов — verified Compose preview screenshot test для каждого примитива.
- **SC-006a (length expansion test)**: Compose preview screenshot tests для каждого `core/ui-senior/` primitive (`SeniorButton`, `SeniorIconButton`, `SeniorTextField`, `SeniorBodyText`, `SeniorTitleText`) MUST render в **трёх locale fixtures**: **EN** (baseline short — «Save» / «Next»), **DE** (long expansion — «Speichern» / «Einstellungen anwenden», ~30-40% длиннее EN), **AR** (RTL + tall glyphs — «حفظ» / «التطبيق»). Verification: 100% pass без clipped text, без overlapping siblings, без layout collapse — verified в CI через Roborazzi или Paparazzi screenshot library.
- **SC-007**: System locale `ar-SA` → wizard отрисовывается RTL (кнопка «Назад» справа, текст выровнен по правому краю) — verified Compose preview screenshot test.
- **SC-008**: Forward-compat read: unknown additive поле в `tile.set.body.tiles[]` молча игнорируется, остальные поля парсятся корректно — 100% pass на test fixture с unknown fields.
- **SC-009**: Hard-fail read: `tile.set` с `schemaVersion: 999` возвращает `IncompatibleVersion(999, 1)`, app показывает hard-fail dialog за < 1 сек после load attempt — verified e2e тестом.
- **SC-010**: APK size delta после F-3 (три новых модуля + 10 locale string tables + bundled test fixtures, **без** S-1 production tile.set / screen.layout) ≤ +1.5 MB к pre-F-3 baseline.
- **SC-011**: Time-to-first-frame главного экрана **не регрессирует** после F-3 (cold start ≤ 1.5 сек на medium-tier эмуляторе — той же baseline что в спеке 010 SC-002). F-3 не добавляет critical-path code в hot start (wizard выполняется **только** при first-run, не на каждом старте).
- **SC-012 (system settings registry)**: `system-settings.pool` JSON roundtrip-тест проходит для `android-pool.json`; `SystemSettingPort.status(...)` для каждой из 6 bundled settings (FR-053a) возвращает валидный `SettingStatus` (не `CheckFailed`) на эмуляторе `pixel_5_api_34` — verified в CI.
- **SC-013 (self-attest UI)**: `SystemSettingStep` с `detectionStrategy = SelfAttest` показывает «Да я сделал / Нет» buttons после возврата пользователя с deep-link Settings экрана. Нажатие «Да» сохраняет `AttestationRecord` в `UserPreferencesStore.attestedSettings` — verified instrumented test.
- **SC-014 (cross-app independence)**: Тестовое app-2 (имитирующее future messenger) в отдельном test fixture вызывает `SystemSettingPort.status("android.accessibility.our-service")` и получает тот же ответ что launcher app-1 — без shared store / IPC. Гарантирует FR-060 design constraint.

### Out of Scope

- **OUT-001**: Конкретный `SimpleLauncherWizardManifest` / `AdminWizardManifest` — это S-1 / S-2 поставляют bundled JSON.
- **OUT-002**: Конкретные production `tile.set` / `screen.layout` файлы для Simple Launcher (starter templates) — S-1 scope.
- **OUT-003**: Reverse-engineering спеки 010 (setup-assistant) — F-3 даёт framework, S-1 расширяет 010 поверх `WizardEngine`. Сам код спеки 010 остаётся как есть до S-1.
- **OUT-004**: Caregiver wizard flow — V-6 (Phase 4).
- **OUT-005**: ~~Translation pipeline~~ — **изменено в C-10**: F-3 **включает** translation pipeline через skill `procedure-translate-spec-strings` (Claude как переводчик). Что **остаётся вне scope**: community contributions UI, professional translation agency integration, alternative AI providers (DeepL/Google Translate/GPT) — adapter pattern есть, конкретный alternative provider — позже.
- **OUT-005a**: Human review для AR/HI/ZH/JA/KK — Phase 4 или при первом native-speaker feedback. F-3 ship'ит AI-generated переводы для всех 10 не-base языков без human review.
- **OUT-006**: `NetworkConfigSource` (server-curated catalog) — additive adapter в Phase 4+, не F-3. Triggered when community sharing UI lands. Запись в `docs/dev/server-roadmap.md` (см. FR-046).
- **OUT-007**: `theme` как отдельная JSON-схема — отвергнуто (glossary §3): темы code-driven через Compose `MaterialTheme`. JSON-тема появится, когда возникнет реальный use case «admin remote-меняет цвета бабушке».
- **OUT-008**: `hint.set` как отдельная JSON-схема — preliminary (glossary §3): отдельный `kind` появится, когда первый реальный hint понадобится в S-1+. В F-3 hint data hardcoded.
- **OUT-009**: `permission.set` как отдельная JSON-схема — это шаг внутри `wizard.manifest` (`PermissionStep`), не документ (glossary §3).
- **OUT-010**: Поля `author`, `createdAt`, `minAppVersion` в JSON header — отложены до non-bundled источников (glossary §4.2).
- **OUT-011**: Общий envelope с `kind`-discriminator — отвергнуто (glossary §5.2): три независимые схемы, путь загрузки сам говорит kind.
- **OUT-012**: Server-Driven UI / runtime JSON-to-Composable интерпретатор — отвергнуто (glossary §5.1): JSON задаёт **данные**, Compose задаёт **рендеринг**.
- **OUT-013**: Выделение в отдельный репозиторий — нет (glossary §7a): только monorepo нарезка на три модуля. Extract trigger — second real consumer.
- **OUT-014**: Maven/JitPack distribution до extract — overhead без consumer'а.
- **OUT-015**: Реальная интеграция `PairingStep` со спекой 007 — F-3 поставляет **stub**; real adapter — в S-2.
- **OUT-016**: Subscription / cloud paywall механика для wizard'а — F-3 чисто local, не касается billing'а.
- **OUT-017**: Admin App / Caregiver / TV wizard variants — все на одном `WizardEngine`, но manifest'ы — это их собственные спеки (S-2, V-6, S-10).
- **OUT-018**: Polished animations / transitions между шагами — baseline implementation (slide / fade). Sophisticated UX motion — design polish в S-1 / S-2.
- **OUT-019 (iOS/TV source sets)**: Per C-3 + C-7, `iosMain` source set в `core/wizard/` и `core/localization/` **НЕ создаётся** в F-3. Compose Multiplatform для `core/ui-senior/` **НЕ применяется** (Android-only Compose). Когда первый iOS launcher (или TV launcher) consumer материализуется — отдельная спека добавит iosMain как additive change в существующие модули; UI пишется с нуля для iOS (либо SwiftUI, либо CMP если он созреет к тому моменту).
- **OUT-020 (cloud sync UX preferences)**: F-3 хранит wizard answers (theme/fontScale/language) локально через `UserPreferencesStore`. Admin remote-control над UX preferences бабушки — goal state, реализуется в отдельной спеке после F-4 (AuthProvider) + cloud sync (additive расширение спеки 008 ConfigDocument). См. FR-051 inline-TODO.
- **OUT-021 (alternative translation providers)**: F-3 фиксирует Claude API как переводчика. DeepL/Google Translate/GPT-4 alternative — adapter pattern (`Translator` port) подразумевается, но не реализован в F-3 (rule 4 MVA — нет потребности в alternative providers сейчас).
- **OUT-022 (Apple Developer Account / iOS signing)**: Per C-17, не блокирует F-3. Owner оплатит ($99/year) когда iOS код начнёт писаться.

---

## Assumptions

### Зависимости от других спеков

- **A-1**: Спека 008 (ConfigDocument) уже определяет runtime ConfigDocument формат. F-3 produces начальный ConfigDocument из `tile.set` + screen.layout + answers; **формат самого ConfigDocument'а** остаётся как в спеке 008, F-3 **не меняет**. Wizard UX preferences (theme/fontScale/language) живут отдельно в `UserPreferencesStore` (Part I); migration в `ConfigDocument.userPreferences` — отдельная спека после F-4 (per C-5 + FR-051 exit ramp).
- **A-2**: Спека 005 (capability registry) определяет actionType strings. Per C-11, F-3 принимает `actionType` как opaque string с опциональным validator hook'ом (FR-014); semantic validation делегируется S-1 (когда F-2 capability registry runtime появится). Inline TODO в `BundledConfigSource` для синхронизации с capability registry.
- **A-3**: Спека 007 (pairing) — F-3 содержит `PairingStep` stub; реальная интеграция со спекой 007 — в S-2.
- **A-4**: Спека 010 (setup-assistant) уже содержит частичный onboarding (ROLE_HOME, POST_NOTIFICATIONS, soft-checks, GMS hard-block). F-3 **не модифицирует** код спеки 010 напрямую; S-1 переписывает 010-функционал поверх `WizardEngine`.

### Архитектурные принципы

- **A-5**: **Reusability discipline** (glossary §7a, смягчённая в C-2): экосистема messenger V-2 / album V-3 планируется в Phase 4, **launcher имеет ценность standalone**. Reuse — цель, не жёсткое требование. Три модуля проектируются launcher-agnostic как discipline tool. Lint rule (FR-038..FR-041) — structural cleanliness, не «load-bearing extraction prerequisite». Extract в отдельный repo откладывается до second real consumer (rule of three).
- **A-6**: **Three independent JSON schemas** (glossary §3): `wizard.manifest`, `screen.layout`, `tile.set` — separate `schemaVersion`, separate roundtrip tests. Server-Driven UI отвергнут.
- **A-7**: **Forward-compat read + hard-fail на breaking** (glossary §4.3 + CLAUDE.md rule 5): additive поля silently ignored, breaking `schemaVersion` → понятный hard-fail.
- **A-8**: **Localization via keys, not literals** (glossary §6): bundled JSON `name`/`description`/`labelKey` — это ключи к `<lang>.strings`. Прямые литералы запрещены (FR-044).
- **A-9**: **Wizard does not edit ConfigDocument** (glossary §2): wizard **produces** первый ConfigDocument; дальнейшее редактирование — отдельная спека (014 Tile Editing).
- **A-10**: **Local-first** (decision [2026-06-15-deferred-cloud](../../docs/product/decisions/2026-06-15-deferred-cloud/)): wizard работает offline, без identity, без cloud. F-4 (Sign-In) активируется в момент первого cloud action, не в F-3.

### Технические допущения

- **A-11** *(REVISED 2026-06-17)*: F-3 НЕ создаёт новые Gradle модули. F-3 добавляет **пакеты внутри существующего `:core`** KMP-модуля, который уже имеет `commonMain + androidMain + iosMain + commonTest + androidUnitTest` source sets + flavor structure (`androidRealBackend`, `androidMockBackend` per spec 007). Это consistent с existing convention.
- **A-12** *(REVISED 2026-06-17)*: UI primitives (`com.launcher.ui.senior`) — **Compose Multiplatform** в `commonMain`, per [ADR-005](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md). iOS support автоматически. Material 3 (`compose.material3`) — foundation theme; senior-safe overrides поверх него.
- **A-13** *(REVISED 2026-06-17)*: **Persistent storage**: F-3 simple key-value stores (Checkpoint, DismissedHints, UserPreferences) → **DataStore Preferences** (`androidx.datastore:datastore-preferences` уже в `core:androidMain`). Если структура усложнится — мигрируем в SQLDelight (consistent с spec 008 `LocalConfigStore` pattern, который уже использует `core:androidMain:sqldelight.android.driver`).
- **A-14** *(REVISED 2026-06-17)*: Per C-8: **Compose Multiplatform Resources** (`compose.components.resources`) — **уже подключено в `core:commonMain`**. moko-resources отвергнут как unnecessary duplicate dependency. Compose Resources Google official, supports plurals + locales + RTL natively.
- **A-15** *(REVISED 2026-06-17)*: Per C-15: **Konsist** — **уже в `libs.versions.toml`** (`konsist` version + dependency) и declared в `core:androidUnitTest` per spec 005 §8 fitness functions. F-3 пишет Konsist test class в том же source set. Никакой spike не нужен.
- **A-15a**: Per C-9: Казахский — `kk-Latn` (латиница) в MVP. Inline-TODO в `GLOSSARY.md`: «когда Казахстан официально завершит переход на латиницу (~2031), `kk-Latn` остаётся base; `kk-Cyrl` добавится как опциональный legacy fallback при первом native-speaker request».
- **A-15b**: Per C-6: **EN — base language**, single source of truth для всех 11 локалей. Это явный override от ADR-004 (если ADR-004 фиксирует другой base — F-3 спека приоритетнее в этом аспекте, и ADR-004 должен быть обновлён в отдельном PR).
- **A-16**: **System locale detection** через Android API (`Resources.configuration.locales[0]`) обёрнуто в `LocaleProvider` port (`fun currentLocaleTag(): String` — возвращает BCP-47 tag). Реальная имплементация в `:app/androidMain` конвертирует `java.util.Locale` → BCP-47 String. Domain layer оперирует **только** BCP-47 String, никогда `java.util.Locale`. Это rule 1 application к locale detection.
- **A-17**: **Wizard analytics** — F-3 эмиссит diagnostic events (`wizardStarted`, `wizardStepCompleted`, `wizardCompleted`, `wizardCancelled`) через `DiagnosticEmitter` port. Конкретный analytics backend (Firebase Analytics / off / custom) — не в F-3 scope.
- **A-18**: **Time abstraction**. `AttestationRecord` (FR-058) и любые future time-stamped F-3 entities используют kotlinx-datetime `Clock` port. Production — `Clock.System`; тесты — `FakeClock` с fixed `Instant`. Это enable'ит deterministic тесты для self-attestation flow.
- **A-19**: **Clean-build time impact**. F-3 добавляет KMP toolchain (для `core/wizard`, `core/localization`) + moko-resources KSP processing + Konsist test dependencies. Оценка clean-build delta: **+1-2 минуты** к pre-F-3 baseline. Justified by JVM testability gain — core/* модули тестируются без эмулятора, что экономит 2-4 минуты на test cycle. Этот trade-off explicit: разовая стоимость build setup vs continual savings в dev loop.

---

## Local Test Path *(mandatory)*

- **Emulator / device**: `pixel_5_api_34` через skill `.claude/skills/android-emulator/SKILL.md` для UI screenshot tests (Android Compose); JVM unit-tests для domain layer (`WizardEngine`, `ConfigSource`, `StringResolver`, `UserPreferencesStore`).
- **Fake adapters used**:
  - `FakeConfigSource` (in-memory, конструируется из Map для каждого теста).
  - `InMemoryCheckpointStore` (тестовая реализация `WizardCheckpointStore`).
  - `InMemoryDismissedHintsStore` (тестовая реализация `DismissedHintsStore`).
  - `InMemoryUserPreferencesStore` (тестовая реализация `UserPreferencesStore`).
  - `FakeLocaleProvider` (позволяет override `currentLocaleTag()` в тестах — задаёт BCP-47 String).
  - `FakeSystemSettingAdapter` (in-memory `SystemSettingPort`, конструируется из `Map<String, SettingStatus>` — для `SystemSettingStep` тестов).
  - `FakeClock` (kotlinx-datetime `Clock` с fixed `Instant` — для deterministic `AttestationRecord` тестов; production использует `Clock.System`).
  - `RecordingDiagnosticEmitter` (capture analytics events для assertion'ов).
- **Fixtures / seed data**:
  - `core/wizard/src/commonTest/resources/fixtures/wizard-manifests/test-app-family.json`
  - `core/wizard/src/commonTest/resources/fixtures/screen-layouts/test-3x4.json`
  - `core/wizard/src/commonTest/resources/fixtures/tile-sets/test-classic-6.json`
  - `core/wizard/src/commonTest/resources/fixtures/forward-compat/tile-set-with-future-fields.json`
  - `core/wizard/src/commonTest/resources/fixtures/hard-fail/tile-set-future-version.json`
  - `core/wizard/src/commonTest/resources/fixtures/system-settings/test-pool.json` (для `SystemSettingStep` тестов — содержит по одной entry каждого `SettingMechanism`)
  - `core/wizard/src/commonMain/resources/system-settings/android-pool.json` (production bundled pool, FR-053a)
  - `core/localization/src/commonTest/resources/fixtures/de.strings-with-gap.properties` (для fallback test'а)
- **Verification commands**:
  - `./gradlew :core:wizard:check` — все wizard unit-tests + lint rule.
  - `./gradlew :core:localization:check` — locale tests + CI fitness function `checkTranslations`.
  - `./gradlew :core:ui-senior:check` — Compose preview screenshot tests + a11y validators.
  - `./gradlew :app:connectedDebugAndroidTest --tests *WizardE2ETest` — process-death resume test, RTL screenshot test (требует эмулятор).
  - `./gradlew checkLauncherAgnosticImports` — fitness function FR-038 (запускается также в `./gradlew check`).
- **Cannot-test-locally gaps**:
  - **TalkBack interactions** для `core/ui-senior/` примитивов — Compose accessibility helpers verified через `Modifier.semantics` assertion'ы (JVM), но реальное TalkBack озвучивание — `// TODO(physical-device)`: проверить на физическом устройстве при первом релизе S-1.
  - **OEM-specific text rendering** (Samsung One UI с custom font, Xiaomi MIUI font scaling) — baseline на Pixel; реальные OEM regressions — `// TODO(physical-device)` per project test policy (per memory `reference_testing_environment.md`).
  - **iOS UI рендеринг** — не верифицируется в F-3 (per C-7 + OUT-019: `core/ui-senior/` Android-only). iOS launcher = отдельная спека / репо в будущем; пишет свой UI с нуля.
  - **Translation pipeline quality для AR/HI/ZH/JA/KK** — generated AI translations не имеют automated quality assessment. Verified только что pipeline проходит (FR-031a) и переводы валидны syntactically. Native-speaker review — Phase 4 (per OUT-005a).

---

## AI Affordance *(mandatory)*

- **Exposable capabilities** (domain verbs, не SDK calls):
  - `wizard.start(appFamilyId): WizardOutcome` — AI agent (future Capability Registry consumer F-2) может программно стартовать wizard для конкретной app-family (например, для restore-from-backup flow).
  - `wizard.skipToStep(stepId): Result` — AI может пропустить пользователя на конкретный шаг (для recovery flow «продолжить с шага X»).
  - `localization.resolve(key, locale, args): String` — read-only capability для AI агента, формирующего user-facing сообщения.
  - `tileSet.list(deviceClass): List<ConfigSummary>` — AI может предложить пользователю starter `tile.set` на основе device class.
- **Required affordances on data**:
  - Read-only доступ к `wizard.manifest` структуре (для AI explanation / debugging).
  - Read-only доступ к `currentLocaleTag()` (BCP-47 String, для AI понимания на каком языке отвечать).
  - **No PII leaves device** через эти capabilities: wizard answers (выбранный tile.set id, fontScale, theme) — не PII; locale code — anonymized.
- **Provider-agnostic shape**: все capabilities выражены как domain ports (FR-002, FR-019, FR-027); никаких Gemini/OpenAI/Claude/MCP types в signatures (CLAUDE.md rule 1 + checklist-capability-registry-readiness).
- **Inline TODO** (per `checklist-capability-registry-readiness`):
  ```kotlin
  // TODO(capability-registry): F-3 wizard / localization / tileSet operations
  // exposed as domain ports. F-2 (Capability Registry Foundation, end of Phase 2)
  // will wrap these into AI-callable capability descriptors. No vendor SDK now.
  ```
- **Out of scope for this spec**: provider implementation, LLM prompt design, MCP server wiring, telemetry — ships в FUTURE-SPEC-AI-* (F-2 + позднее).

---

## OEM Matrix *(mandatory)*

F-3 — частично touches device behavior (persistent storage для checkpoints, font scaling реакция на system settings, locale detection). Полная OEM-specific divergence — minimal для этих surface'ов.

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|-------------------------|---------------------|
| Stock Android (Pixel) | baseline | — | эмулятор `pixel_5_api_34` |
| Samsung One UI | Custom system font (`SamsungOne`) меняет text metrics; может вызвать обрезку текста на baseline 56dp | `SeniorButton` использует `fontScale`-aware sizing; baseline 56dp — minimum, не fixed; `wrapContentHeight()` для авто-роста | `// TODO(physical-device)`: проверить на Galaxy S21 / S22 при первом S-1 alpha |
| Xiaomi MIUI | MIUI agresivно kill'ит background processes — checkpoint write должен быть synchronous (не WorkManager async) | `PersistentCheckpointStore` пишет checkpoint в **synchronous** suspend function до return из step; не использует WorkManager | `// TODO(physical-device)`: kill-test на Redmi Note (per memory `reference_testing_environment.md` — нельзя сейчас, отложено) |
| Huawei EMUI (без GMS) | F-3 чисто local — GMS не требуется (Firebase used только в S-* спеках). | F-3 build не имеет hard dependency на GMS; работает на GMS-less устройствах | Pixel эмулятор + manual review кода на отсутствие Google Play SDK imports в core/* |
| RTL locales (AR/HI) на любом OEM | Layout direction должен зеркалироваться | `LocaleProvider` + Compose `LocalLayoutDirection` обеспечивают auto-mirror; verified RTL screenshot test | Compose preview screenshot test на эмуляторе |
| Samsung One UI — Accessibility settings путь | Samsung прячет Accessibility под другой иерархией («Доступность → Установленные сервисы», не просто «Accessibility»). Deep-link `Settings.ACTION_ACCESSIBILITY_SETTINGS` всё ещё работает, но landing экран отличается визуально | `AndroidSystemSettingAdapter` использует стандартный intent action — Samsung уважает. Дополнительный `extendedInstructionKey` в pool entry содержит OEM-aware текст (см. FR-053) | `// TODO(physical-device)`: проверить flow на Galaxy при первом S-1 alpha |
| Xiaomi MIUI — Autostart + special permissions | MIUI добавляет дополнительный «Autostart» toggle (нет стандартного intent action) + прячет «Display popup window in background» под другой путь | F-3 pool НЕ содержит MIUI-specific entries в MVP. Inline-TODO в `android-pool.json`: «MIUI autostart entry — TBD когда первый MIUI bug report». Без этого MIUI пользователи получат частично работающий launcher | `// TODO(physical-device)`: тест на Redmi Note (per memory `reference_testing_environment.md` — нельзя сейчас, отложено) |
| Huawei EMUI — Protected Apps | EMUI требует app быть в «Protected Apps» для background work | F-3 pool НЕ содержит EMUI entry. Inline-TODO. Без него F-3 будет работать, но S-1 background features могут страдать | `// TODO(physical-device)` |

---

## Зависимости и cross-spec impact

### Артефакты, которые F-3 модифицирует вне `specs/015-*/`

- **[docs/dev/server-roadmap.md](../../docs/dev/server-roadmap.md)** — добавить **две** записи:
  - триггер `NetworkConfigSource` (FR-046): «активируется при появлении community sharing UI в Phase 4+; до тех пор bundled остаётся sole source».
  - триггер migration `UserPreferencesStore` → `ConfigDocument.userPreferences` (FR-051): «активируется когда F-4 (AuthProvider) + cloud sync infrastructure ready; additive расширение спеки 008 ConfigDocument schemaVersion bump».
- **[docs/dev/project-backlog.md](../../docs/dev/project-backlog.md)** — закрытие `TODO-FUTURE-SPEC-006` (onboarding-and-tutorials поглощается F-3 через `TutorialHintManager` + `TutorialHintStep`).
- **[docs/product/roadmap.md](../../docs/product/roadmap.md) §Шаг 1 F-3** — обновить статус (Draft → InProgress → Done) по мере прохождения фаз.
- **[docs/dev/adrs/ADR-004-localization.md](../../docs/dev/adrs/)** — обновить (либо явный override в этой спеке): base language = EN (per C-6 + A-15b). Если ADR-004 ранее фиксировал другой base — нужен отдельный PR с update.
- **Root `build.gradle.kts` / `settings.gradle.kts`** — добавить три новых модуля (`core/wizard/`, `core/localization/`, `core/ui-senior/`), включить Konsist fitness function task в `check`.
- **`.claude/skills/procedure-translate-spec-strings/`** — создать новый skill (per C-10 + FR-031a). Запускается в конце `speckit-tasks`. Генерирует переводы для 10 не-base языков через Claude API.
- **`.claude/skills/checklist-preset-readiness/`** — создать skill (per user prompt, против unification erosion). Опционально: `checklist-shareability` (rule 9 enforcement).
- **`core/localization/GLOSSARY.md`** — создать с initial set canonical терминов (Tile, Wizard, Admin, Managed, Senior) + tone guidelines per language.
- **`core/localization/strings-context/CONTEXT.json`** — создать с empty schema; per-key context добавляется когда добавляется key.
- **`docs/dev/capability-registry-pending.md`** — добавить (либо создать файл если не существует) entries для F-3 capabilities, чтобы F-2 (Phase 2) мог их собрать без archaeology task:
  - `wizard.start(appFamilyId)` — write; idempotent; reversible; auth: device-local.
  - `wizard.skipToStep(stepId)` — write; idempotent; reversible; auth: device-local.
  - `localization.resolve(key, args)` — read-only; idempotent; pure read; auth: device-local.
  - `tileSet.list(deviceClass)` — read-only; idempotent; pure read; auth: device-local.
  - `systemSettings.applyOrPrompt(settingId)` (per каждый entry в `android-pool.json`, FR-053a) — write; idempotent (повторный prompt не меняет state); reversible через Android Settings; auth: device-local.
  Каждая entry с 1-line description. F-2 будет использовать этот index для enumeration capabilities при wrapping в descriptors.
- **Спека S-1 (Simple Launcher, future)** — после F-3 done, S-1 поставляет конкретные `SimpleLauncherWizardManifest`, `screen.layout` (3×4 default), `tile.set` (classic-6, classic-9), переписывает функциональность спеки 010 поверх `WizardEngine`.
- **Спека S-2 (Admin App, future)** — после F-3 done, S-2 поставляет `AdminWizardManifest`, подключает real `PairingStep` adapter поверх спеки 007.

### Cross-spec потребители F-3 (downstream)

- S-1, S-2, S-3 (Simple Launcher / Admin App / TV) — все используют `WizardEngine`.
- Все спеки Phase 1+ — используют `core/localization/` для строк.
- Все UI спеки Phase 1+ — используют `core/ui-senior/` для elderly-friendly primitives.
- F-2 (Capability Registry Foundation, end of Phase 2) — обернёт capabilities из AI Affordance section в descriptors.

---

## TL;DR на русском *(per skill `procedure-add-novice-summary`)*

Этот спек **не делает ничего видимого пользователю напрямую**. Он создаёт **фундамент**, на котором следующие спеки строят видимые вещи.

**Что делается** (тремя модулями):

1. **`core/wizard/`** — «движок мастеров настройки». В нашем приложении (и в будущих приложениях — мессенджере, фотоальбоме) при первом запуске пользователь проходит wizard: выбирает язык, размер шрифта, тему, раскладку плиток, разрешает доступ к чему нужно. **Каждое приложение объявляет JSON-файлом**, какие шаги ему нужны и в каком порядке. Один движок — много приложений.
2. **`core/localization/`** — локализация на **11 языков**. **Базовый язык — английский** (single source of truth). Остальные 10: RU, ES, ZH, AR, HI, PT, DE, FR, JA, kk-Latn (казахский латиница). Все тексты в коде и в JSON — это **ключи** (типа `wizard.next_button`), а не сами слова. Переводы генерирует Claude (это я) автоматически в конце каждого спека через специальный skill — ты только пишешь английский + русский (для удобства), остальные 9 генерируются. Если в каком-то языке забыли перевод — **CI ломает сборку** (не warning, а fail).
3. **`core/ui-senior/`** — большие кнопки (минимум 56dp вместо стандартных 48dp), тёплая контрастная тема, шрифт автоматически растёт если у бабушки system fontScale большой. **Android-only Compose** (не Compose Multiplatform) — это базовый «конструктор» senior-friendly интерфейса для Simple Launcher / Admin App / TV. Когда дойдёт до iOS — iOS UI будет отдельно (либо SwiftUI, либо CMP к тому моменту созреет).

**Четыре JSON-схемы** (одинаковый 6-полевой заголовок):
- **`wizard.manifest`** — какие шаги показывать в wizard'е (для каждого приложения свой).
- **`screen.layout`** — каркас экрана (сетка 3×4, нижняя панель, табы).
- **`tile.set`** — обезличенный стартовый набор плиток (где какая, какое действие, ключ-имени, ключ-иконки).
- **`system-settings.pool`** — реестр доступных системных настроек на платформе (для Android: ROLE_HOME, POST_NOTIFICATIONS, accessibility service, hide статус-бар и т.д.). Для каждой настройки описано: как открыть экран Android Settings (deep-link), как проверить что пользователь применил (программно или через «Да я сделал»), какой текст показывать. **Один pool — много wizard'ов** (launcher и messenger могут ссылаться на одни и те же setting'и). iOS / TV pools — добавятся когда дойдут эти платформы.

**Защита от регресса**: автоматический lint (Konsist), который **ломает сборку**, если кто-то случайно из `core/*` импортирует что-то из `app/*`. Это **discipline tool** — держит модули в чистоте. Когда дойдёт до выделения в отдельную библиотеку для messenger / album (Phase 4), эта дисциплина окупится — extraction будет проще.

**Куда едут ответы wizard'а** (тема / размер шрифта / язык override): локально в `UserPreferencesStore` (отдельное хранилище в `app/`). Через год-полтора, когда появится F-4 (Sign-In) и cloud sync — мигрируем в общий ConfigDocument (admin сможет remote-менять тему бабушке). Сейчас это локально — простая и правильная архитектура для Phase 1.

**Что делать когда настройка глубокая** (типа «скрыть системную шторку») — три способа применения, по сложности:
1. **Простое разрешение** (звонки, контакты) — стандартный диалог, проверить программно — лёгко.
2. **Глубокое разрешение** (overlay, write settings, доступ к статистике использования) — открываем Android Settings экран через deep-link, пользователь тапает toggle, мы потом проверяем.
3. **Accessibility Service** (скрыть шторку, заблокировать кнопки громкости) — мы не можем «попросить разрешение», мы должны сделать **наш сервис частью Android Accessibility**. Открываем Settings → Accessibility → пользователь включает наш сервис → после этого наш сервис **сам что-то делает**. F-3 умеет открывать deep-link и проверять что сервис активирован. Конкретный сервис (что блокировать) пишется в S-1 (Simple Launcher).

Когда **нет способа проверить программно** — wizard спрашивает «Вы это сделали? Да / Нет», ответ сохраняется как **self-attestation** (бабушка тапнула «Да»). Если потом feature не работает — баннер в Settings напомнит.

**Cross-app**: launcher настроил accessibility service — messenger потом проверит ту же системную штуку и узнает результат. Нет shared store / IPC между приложениями — Android сам как источник правды.

**Что важного НЕ входит**:
- Конкретные wizard'ы для Simple Launcher и Admin App (это будущие спеки S-1 и S-2).
- Конкретные стартовые наборы плиток (тоже S-1).
- Облачная синхронизация / Sign-In (F-4, активируется когда понадобится cloud, не сейчас).
- Pairing с другим устройством (только заглушка; реальная интеграция в S-2).
- **iOS / TV**: пока не закладываем. Бизнес-логика (`core/wizard/`, `core/localization/`) в KMP-формате — это даёт переносимость на iOS почти бесплатно. UI (`core/ui-senior/`) — Android-only сейчас; iOS UI будет писаться с нуля когда дойдёт iOS launcher.

**Зачем сейчас**: без F-3 ни один следующий шаг Phase 1 невозможен — у нас просто нет инфраструктуры для запуска приложения, для локализации и для senior-friendly UI. F-3 — это первый шаг, после которого появляется всё остальное.

**Effort**: Large, ~3-4 недели (+1 неделя к исходной оценке из-за добавления `core/ui-senior/` модуля и lint rule fitness function).
