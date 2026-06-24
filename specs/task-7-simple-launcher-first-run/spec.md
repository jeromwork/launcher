# Feature Specification: Simple Launcher Profile (first MVP-demo)

**Feature Branch**: `task-7-simple-launcher-first-run`
**Created**: 2026-06-24 (rewritten after architecture clarification + constitution amendment 1.7)
**Status**: Draft
**Input**: User description: backlog [TASK-7](../../backlog/tasks/task-7%20-%20Simple-Launcher-first-run-Setup-Wizard.md) S-1. Ship `simple-launcher` profile as composition of bundled JSON documents on top of the existing wizard engine (TASK-1 / F-3 Done). Constitution Article VII §9–13 (profile = composition, не код). LOCAL mode, без cloud.

---

## Контекст и цель

**Архитектурная модель** (constitution Article VII §9–13, glossary §2).

Приложение — generic shell. Его поведение для конкретного product variant'а определяется **профилем** (`profile`) — именованной композицией bundled JSON-документов. `simple-launcher` — это **первый concrete profile**, валидирующий модель: elderly-friendly handheld variant с большими плитками, тёплыми цветами, минимумом choices.

**Что уже есть в коде** (TASK-1 / F-3 Done):
- `WizardEngine`, `WizardManifest`, `ConfigSource`, `SystemSettingPort`, `StringResolver`, `TutorialHintManager`, `UserPreferencesStore`, `WizardCheckpointStore` — все ports + adapters.
- Bundled pools: [`system-settings/android-pool.json`](../../core/src/androidMain/assets/wizard/system-settings/android-pool.json) (6 entries: ROLE_HOME, POST_NOTIFICATIONS, CALL_PHONE, accessibility-service, battery-optimization, hide-status-bar) и [`ui-customization/ui-pool.json`](../../core/src/androidMain/assets/wizard/ui-customization/ui-pool.json) (6 options: language, theme, fontScale, grid, screenLayout, tileSet).
- Bundled documents: один `screen.layout` ([`3x4-classic.json`](../../core/src/androidMain/assets/wizard/screen-layouts/3x4-classic.json)) и один `tile.set` ([`classic-6.json`](../../core/src/androidMain/assets/wizard/tile-sets/classic-6.json)).
- Wizard manifest [`wizard-manifests/simple-launcher.json`](../../core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json) **существует, но почти пустой**: `steps: null`, `autoOrder: true`.

**Что TASK-7 делает** (delta scope):
1. Заполнить `simple-launcher.wizard.manifest.json` явными `steps` (вместо `autoOrder: true`), с per-step `canSkip` override'ами специфичными для этого профиля.
2. Добавить недостающие bundled `screen.layout` и `tile.set` JSON'ы (если требуется варианты под grid choices).
3. Локализованные strings (en + ru минимум) под все ключи которые simple-launcher flow задействует.
4. Integration tests + senior-safe walkthrough verification.
5. End-to-end проверка skip-with-banner для simple-launcher специфично (механизм инфраструктурный делегирован spec 010 / `SetupCheckRegistry`).

**Actor wizard'а**: не primary user (пожилой человек). Wizard проходит **помощник / assisting family member** — родственник во время визита, платный помощник, IT-support, медсестра, или в self-care варианте сам primary user. Per CLAUDE.md «Personas vs domain roles».

**LOCAL mode**: без Google Sign-In, без cloud. Cloud features deferred per decision 2026-06-15-deferred-cloud/01.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Fresh install → wizard → working home screen (Priority: P1)

`Assisting family member` устанавливает APK на телефон `primary user`'а. Открывает приложение. Cold-start ≤ 2 сек до первого экрана wizard'а. Engine читает `simple-launcher.wizard.manifest.json` через `ConfigSource`. Помощник последовательно проходит mandatory шаги (язык, ROLE_HOME, POST_NOTIFICATIONS, выбор сетки, выбор tile.set), опционально настраивает optional (тема, fontScale, theme, остальные системные настройки). По завершении wizard'а ≤ 1 сек — `HomeActivity` показывает **реальные плитки** из выбранного tile.set + screen.layout композиции.

**Why this priority**: главный demo-критерий продукта. Без US-1 продукта не существует — есть только инфраструктура.

**Independent Test**: эмулятор `pixel_5_api_34`, fresh install APK, открыть → wizard → пройти mandatory → home screen с плитками виден.

**Acceptance Scenarios**:

1. **Given** свежеустановленный APK на эмуляторе, никакого state в DataStore, **When** помощник открывает app первый раз, **Then** wizard launcher активность открывается ≤ 2 сек после tap'а на иконку; первый экран wizard'а — language step (или другой первый шаг по manifest order'у).
2. **Given** помощник прошёл все mandatory шаги без skip'ов, **When** последний mandatory шаг confirmed, **Then** wizard завершается → `HomeActivity` открывается ≤ 1 сек, рендерит выбранный `tile.set` поверх выбранного `screen.layout`. Плитки реально содержат `actionType` записей из tile.set (placeholder без contactId — реальные контакты в TASK-9).
3. **Given** помощник на одном из шагов, **When** swipes app из recents (kill), **Then** при повторном open wizard продолжается с того же step'а (`WizardCheckpoint` persistence — уже сделано в TASK-1).
4. **Given** wizard завершён, ROLE_HOME granted, **When** помощник нажимает Home button на устройстве, **Then** наш `HomeActivity` открывается, плитки отрисованы из применённого config'а.

---

### User Story 2 — Mandatory vs optional + skip-with-banner (Priority: P1)

`simple-launcher` манифест объявляет какие шаги mandatory (нельзя пройти дальше без apply'а), какие optional with skip-banner (skip'ается кнопкой «Позже», в Settings висит баннер «настрой это»), какие optional silent (не trigger'ит banner). Mandatory/optional/skip semantics per constitution Article VII §12 — pool defaults + per-profile override через manifest.

**Why this priority**: skip-with-banner — это **the** UX механика wizard'а; без неё помощник вынужден либо проходить всё либо ничего. Соответствие constitution требованию.

**Independent Test**: пройти wizard, на theme шаге (по умолчанию canSkip=true в ui-pool) нажать «Позже» → wizard продолжается → exit → home screen рендерит с дефолтной темой; в Settings виден баннер «Тема не настроена», тап → standalone theme picker открывается → выбор сохраняется → баннер исчезает.

**Acceptance Scenarios**:

1. **Given** wizard на mandatory шаге (например, language — `criticality: Required, canSkip: false` в ui-pool), **When** помощник пытается нажать «Назад» или закрыть, **Then** UI **не позволяет skip** — нет кнопки «Позже»; для system-setting шагов где Android отказал — wizard остаётся на том же шаге с retry.
2. **Given** wizard на optional шаге с canSkip=true (например, theme), **When** помощник нажимает «Позже», **Then** wizard переходит к следующему шагу; engine записывает skip в `WizardOutcome.Completed.userPreferences` (TASK-1 path) или через `SetupCheckRegistry` (spec 010); банер в Settings виден после wizard exit'а.
3. **Given** в `simple-launcher.wizard.manifest.json` для шага ROLE_HOME `canSkip` override'ит pool default `true` на `false`, **When** помощник на шаге ROLE_HOME отказывается в системном диалоге, **Then** wizard **не позволяет proceed** — остаётся на том же шаге с inline retry button (через `SystemSettingPort.applyOrPrompt()`).
4. **Given** помощник скипнул theme шаг через кнопку «Позже», **When** позже открывает Settings (через 7-tap или прямой entry per profile), **Then** баннер «Тема не выбрана — настроить?» виден; tap «Настроить» → открывается **тот же** UIChoiceStep (theme picker) standalone, не reset wizard'а с шага 1.
5. **Given** ни одного `[NEEDS CLARIFICATION: per-step canSkip override list для simple-launcher — какие конкретно шаги должны быть overridden vs принимают pool default?]`. Нужно зафиксировать конкретный список override'ов в manifest'е.

---

### User Story 3 — Permission denial → graceful retry (Priority: P2)

`Assisting family member` на шаге ROLE_HOME (или POST_NOTIFICATIONS, или любого SystemSettingStep с Required + canSkip=false) **отказывает** в системном диалоге Android. Wizard не падает, не выбрасывает на главный экран — вежливо просит повторно через `SystemSettingPort.applyOrPrompt()` (открывает системный Settings deep-link для ROLE_HOME, либо `Settings.ACTION_APP_NOTIFICATION_SETTINGS` для POST_NOTIFICATIONS).

**Why this priority**: real-world сценарий — помощник может промахнуться, может не понять диалог. P2 потому что mandatory шаги все равно блокируют exit, но UX надо не frustrировать.

**Independent Test**: на эмуляторе с TalkBack off, дойти до ROLE_HOME шага → в системном диалоге выбрать «отменить» → проверить что wizard остался на том же шаге с retry button → tap retry → диалог снова открыт.

**Acceptance Scenarios**:

1. **Given** wizard на ROLE_HOME шаге, ROLE_HOME pool entry с `canSkip: false` override'ом для simple-launcher, **When** помощник в системном `RoleManager` диалоге отказался, **Then** wizard остаётся на ROLE_HOME шаге, показывает rationale «Без этого Home button работать не будет — попробуй ещё раз» + retry button.
2. **Given** wizard на POST_NOTIFICATIONS шаге, Android 13+, помощник `permanently denied` (отметил «не спрашивать больше»), **When** wizard пытается re-prompt, **Then** вместо системного диалога — deep-link на `Settings.ACTION_APP_NOTIFICATION_SETTINGS`; помощник вручную включает → возвращается → `SystemSettingPort.status()` detect Applied → wizard переходит к следующему шагу.
3. **Given** Android < 13, **When** wizard на POST_NOTIFICATIONS шаге, **Then** шаг **автоматически пропускается** (per `androidMinApi: 33` в pool entry); engine это уже умеет.

---

### User Story 4 — Locale change → strings switch after restart (Priority: P2)

`Assisting family member` выбрал русский в language шаге wizard'а. Прошёл wizard. Позже кто-то изменил системную локаль Android на английский. После app restart strings приложения переключаются на английский (default behavior через resource resolution), **либо** остаются русскими если app сохранил locale override `[NEEDS CLARIFICATION: persist user locale choice via AppCompatDelegate.setApplicationLocales() — yes / no?]`.

**Why this priority**: i18n MUST per ADR-004 и constitution Article VII §3 (validation). P2 потому что edge case для primary user'а пожилого, но MUST для multi-locale устройств.

**Independent Test**: эмулятор с системной локалью `en-US`, fresh install → wizard → язык auto-detect English → switch на Русский → strings переключаются live → wizard завершён → kill app → системная локаль `en-US` всё ещё → open app → проверить language.

**Acceptance Scenarios**:

1. **Given** эмулятор с системной локалью `en-US`, **When** wizard открывается на language шаге, **Then** auto-detected default — `en` (per `defaultValue: "en"` в ui-pool); все strings — на английском.
2. **Given** wizard на language шаге, **When** помощник выбирает `ru` из choices, **Then** strings всего wizard'а переключаются на русский немедленно через `StringResolver` (уже сделано в TASK-1).
3. **Given** wizard завершён с `languageOverride: "ru"`, системная локаль `en-US`, **When** kill + open app, **Then** UI — на русском (если AppCompatDelegate override applied) или на английском (если только wizard-time choice, нет persistent override) — поведение зависит от clarify Q4.
4. **Given** unsupported локаль (`zh-CN` но в ui-pool choices только en/ru/es/zh/ar/hi/pt/de/fr/ja/kk-Latn — `zh` есть), **Then** fallback на ближайший supported choice или на default per `defaultValue`.

---

### User Story 5 — Reboot persistence (Priority: P3)

После wizard'а: `wizardCompletedManifest` сохранён persistent (per F-3 architecture). Reboot устройства → wizard не повторяется; `HomeActivity` открывается с применённой композицией.

**Why this priority**: edge case robustness; механизм уже инфраструктурно сделан в TASK-1.

**Acceptance Scenarios**:

1. **Given** wizard завершён, applied configuration в `/config/current`, **When** `adb reboot`, **Then** после reboot Home button → наш `HomeActivity` (если ROLE_HOME granted), плитки те же.
2. **Given** wizard незавершён (kill в середине), **When** open app, **Then** wizard продолжается с того же step'а через `WizardCheckpoint`.

---

### User Story 6 — Senior-safe walkthrough verification (Priority: P3)

`Assisting family member` проходит wizard без подсказок на эмуляторе через skill `android-emulator`. Verification — manual gate `[hand]`. Per constitution Article VIII §7 senior-safe baseline (≥ 56dp tap targets, ≥ 24sp text, ≥ 4.5:1 contrast). Wizard sized для assisting (не для elderly directly), но senior-safe baseline MUST держится поскольку primary user может тоже навигировать.

**Acceptance Scenarios**:

1. **Given** эмулятор с TalkBack enabled, **When** проходит wizard, **Then** каждый actionable element имеет `contentDescription`; focus order осмыслен.
2. **Given** font size в Android Settings `largest` (150%), **When** проходит wizard, **Then** все strings помещаются без обрезания.
3. **Given** senior-safe walkthrough manual через skill `android-emulator`, **When** AI или тестировщик проходит wizard, **Then** все шаги понятны без помощи документации.

---

### Edge Cases

- **GMS-less устройство** (Huawei post-2019): spec 010 FR-042 hard-block screen **до** wizard'а. TASK-7 wizard не запускается. Regression test, не дублирует.
- **Concurrency wizard ↔ admin push**: в LOCAL mode админских push'ей нет. Edge case не возникает.
- **Bundled JSON corruption на disk**: если malformed → `ConfigSourceResult.ParseError`. UI показывает error screen «не могу прочитать профиль» с кнопкой «Попробовать снова» (re-load) `[NEEDS CLARIFICATION: точный UX error path — restart app, retry, fallback на minimal layout?]`.
- **`schemaVersion` mismatch**: если bundled JSON имеет `schemaVersion > known` — `IncompatibleVersion` result; engine treats as invalid, starts wizard from step 0 (F-3 behavior).
- **Wizard kill во время системного диалога Android**: после restart engine restores `WizardCheckpoint`, retry того же шага. F-3 уже это делает.
- **OEM-specific системный диалог ROLE_HOME** (Samsung One UI добавляет confirm dialog): wizard ждёт результата через `SystemSettingPort.status()` после возврата фокуса — Android lifecycle обрабатывает.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Part A — `simple-launcher.wizard.manifest.json` content authoring

- **FR-001**: System MUST содержать [`simple-launcher.wizard.manifest.json`](../../core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json) с **явным** `steps` массивом (вместо `autoOrder: true`). Engine читает manifest через `ConfigSource` (уже сделано).
- **FR-002**: Manifest MUST содержать следующие steps в этом order'е `[NEEDS CLARIFICATION: финальный список + порядок — нужно зафиксировать в clarify]`:
  1. `UIChoice` → `refId: "language"` (Required, canSkip: false — pool default).
  2. `SystemSetting` → `refId: "android.role.home"` (canSkip: **false** — override pool default `true`).
  3. `SystemSetting` → `refId: "android.permission.POST_NOTIFICATIONS"` (auto-skip on Android < 13 per pool `androidMinApi: 33`).
  4. `UIChoice` → `refId: "grid"` (или `screenLayout` — нужно решить в clarify).
  5. `UIChoice` → `refId: "tileSet"` (Required, canSkip: false — pool default).
  6. `UIChoice` → `refId: "theme"` (Optional, canSkip: true — pool default).
- **FR-003**: Per-step `canSkip` override'ы в manifest'е MUST использовать pool entry default если override не нужен. Для `simple-launcher` минимум один override: `android.role.home` с `canSkip: false` (pool default `true`).
- **FR-004**: Если pool entry имеет `androidMinApi: N`, engine MUST auto-skip step на устройствах с `Build.VERSION.SDK_INT < N` (уже сделано в F-3). simple-launcher manifest полагается на это — POST_NOTIFICATIONS auto-skip на API < 33.

#### Part B — Bundled `screen.layout` and `tile.set` documents

- **FR-005**: System MUST содержать `screen.layout` документы покрывающие grid choices в `ui-pool` (`2x3`, `3x4`, `4x5`). `[NEEDS CLARIFICATION: один параметризованный screen.layout где gridRows/Cols берутся из user choice, OR три отдельных bundled документа (`2x3-classic`, `3x4-classic`, `4x5-classic`)? Сейчас один — 3x4-classic.]`
- **FR-006**: System MUST содержать `tile.set` документы покрывающие разумные начальные раскладки. Сейчас один — `classic-6` с 6 плитками (phone, messages, camera, gallery, contacts, settings). `[NEEDS CLARIFICATION: нужны ли additional tile.set'ы — например, `classic-9`, `classic-12`, или семейные / клиник варианты — в TASK-7 scope?]`
- **FR-007**: Все bundled `tile.set` документы MUST содержать только **placeholder** entries: `actionType` строки (типа `"phone.call"`, `"messages.open"`), без real `contactId` или identity-bound данных (CLAUDE.md rule 9). Real contacts — TASK-9.
- **FR-008**: Все bundled документы MUST соответствовать wire-format header (per glossary §4.1): `schemaVersion: 1`, `id: "<kind>.<slug>"`, `name: "<localization_key>"`, `description: "<localization_key>"`, `deviceClass: ["android-phone"]` (или `["*"]`).
- **FR-009**: Roundtrip test MUST cover каждый новый bundled документ (read JSON → deserialize → serialize → assert equal) per CLAUDE.md rule 5.
- **FR-010**: Backward-compat test MUST cover `schemaVersion: 1` чтение через future-version reader (CLAUDE.md rule 5).

#### Part C — Localization

- **FR-011**: All localization keys referenced в `simple-launcher.wizard.manifest.json`, в pool entries которые simple-launcher задействует, и в bundled `screen.layout` / `tile.set` MUST иметь records в `strings.xml` (или Moko Resources) для en + ru минимум.
- **FR-012**: Конкретные keys которые simple-launcher вводит: `wizard_manifest_simple_launcher_name`, `wizard_manifest_simple_launcher_desc`, плюс labelKey/descriptionKey/questionKey всех задействованных pool entries (уже частично есть от TASK-1).
- **FR-013**: Missing key → fallback на EN per ADR-004; никакого hardcoded русского текста в Kotlin-коде / JSON-литералах.

#### Part D — Integration with existing infrastructure

- **FR-014**: Wizard exit MUST trigger applied configuration (через `ConfigEditor.apply()` или эквивалент). Home renderer (existing) MUST подхватывать применённый `tile.set` + `screen.layout` композицию.
- **FR-015**: Skip mandatory step с canSkip=true MUST регистрироваться в `SetupCheckRegistry` (spec 010 / orphan но код есть) → banner в Settings виден после wizard exit'а. TASK-7 валидирует end-to-end интеграцию для simple-launcher specifically.
- **FR-016**: Wizard MUST использовать `WizardCheckpoint` для persistence promezhutochnogo state'а (F-3 path). Kill app в середине wizard'а → reopen → continue from same step.
- **FR-017**: Wizard MUST использовать `UserPreferencesStore` для persistent user choices (theme, fontScale, languageOverride) per F-3 data model (`UserPreferences` data class).

#### Part E — Senior-safe + accessibility

- **FR-018**: Wizard UI MUST использовать Senior UI primitives (`SeniorButton`, `SeniorBodyText`, `SeniorTitleText`, `SeniorWarmTheme`) из TASK-1. Все actionable elements ≥ 56dp tap target (constitution Article VIII §7 senior-safe baseline).
- **FR-019**: Все strings ≥ 18sp body / ≥ 24sp title; line-height 1.5×.
- **FR-020**: Contrast ratio ≥ 7:1 для critical text (Article VIII senior-safe override).
- **FR-021**: TalkBack semantics: каждый actionable element имеет `contentDescription`; focus order top-to-bottom; нет focus traps.

#### Cross-cutting

- **FR-022**: TASK-7 MUST NOT добавлять новых Gradle-модулей кода dedicated для simple-launcher (constitution Article VII §13).
- **FR-023**: TASK-7 MUST NOT добавлять code branches keyed on `appFamilyId == "simple-launcher"` в business logic (constitution Article VII §13).
- **FR-024**: TASK-7 MUST NOT вводить новые `ConfigKind` enum entries (constitution Article VII §10) — задействует существующие пять.
- **FR-025**: TASK-7 MUST NOT добавлять новых ports / domain types в `core/commonMain/api/wizard/` (CLAUDE.md rule 4 MVA + Article XI Simplicity). Если выяснится в plan, что без нового port'а не обойтись — это **поднимется в clarify как exception** с обоснованием.

### Key Entities

**TASK-7 не вводит новых сущностей в коде** — это content authoring + integration. Сущности задействованные:

- **`WizardManifest`** (F-3 data model) — заполняется simple-launcher.wizard.manifest.json.
- **`StepEntry`** (F-3) — каждая запись `steps[]` в manifest'е.
- **`ConfigKind`** enum (F-3) — references на pool entries / bundled documents.
- **`SystemSettingEntry`** (F-3) — записи в android-pool которые simple-launcher задействует.
- **`UIOptionEntry`** (F-3) — записи в ui-pool которые simple-launcher задействует.
- **`ScreenLayout`** (F-3 data model) — bundled documents.
- **`TileSet`** (F-3 data model) — bundled documents.
- **`UserPreferences`** (F-3 data model) — produced by wizard outcome.

Где сущность кажется новой — это **локализация key** (string resource), не Kotlin entity.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Помощник установил APK на эмулятор → wizard первый экран виден ≤ 2 сек после tap'а на иконку.
- **SC-002 [backlog]**: Помощник прошёл все mandatory шаги → `HomeActivity` рендерит выбранную композицию (screen.layout + tile.set) ≤ 1 сек после wizard exit'а.
- **SC-003 [backlog]**: Перезагрузил устройство → wizard не повторяется; HomeActivity открывается с применённой композицией.
- **SC-004 [backlog]**: Пропустил optional шаг (theme при canSkip=true) → в Settings висит баннер; tap → standalone step → выбор сохраняется → баннер исчезает.
- **SC-005 [backlog]**: Отказал в ROLE_HOME (canSkip=false override) → wizard остаётся на шаге с retry, не падает.
- **SC-006 [backlog]**: Изменил системную локаль Android → strings wizard'а сменились (детали поведения зависят от clarify Q про locale override).
- **SC-007 [backlog]**: Senior-safe walkthrough на эмуляторе через skill `android-emulator` — assisting проходит wizard без подсказок (manual `[hand]` AC).
- **SC-008**: Roundtrip test проходит для каждого нового bundled JSON (CLAUDE.md rule 5).
- **SC-009**: Backward-compat test проходит — `schemaVersion: 1` JSON читается через future-version reader без потери fields.
- **SC-010**: Fitness function / Konsist test проходит: нет новых Gradle модулей dedicated для simple-launcher; нет `if (appFamilyId == "simple-launcher")` branches в business logic; нет новых `ConfigKind` enum entries.
- **SC-011**: APK size delta ≤ +50 KB (только JSON + strings, без нового кода).

### Out of Scope

- **OUT-001**: Admin App profile (TASK-8 / S-2).
- **OUT-002**: Contact tiles content (TASK-9 / S-3 — placeholder tiles в TASK-7).
- **OUT-003**: SOS configuration (TASK-10 / S-4).
- **OUT-004**: Photo upload / display (TASK-11 / S-5).
- **OUT-005**: Caregiver remote invite (TASK-31 / V-6).
- **OUT-006**: Adaptive-UX presets (TASK-19 / P-4) — sub-feature внутри профиля per Project-Specific Direction §5; в TASK-7 не enabled.
- **OUT-007**: Google Sign-In step (deferred per decision 2026-06-15-deferred-cloud/01).
- **OUT-008**: Real QR pairing integration в wizard. spec 007 / TASK-? зависимость не подтверждена. В TASK-7: либо отсутствует, либо minimal stub (button → external Settings entry).
- **OUT-009**: Theme editing post-wizard через separate Settings entry — `[NEEDS CLARIFICATION: в TASK-7 scope или отдельная задача?]`. Default — out: theme settable только через wizard / banner.
- **OUT-010**: Tutorial overlays / hints — TutorialHintManager port есть, но конкретные hints в TASK-7 scope не входят (`[NEEDS CLARIFICATION: какие hints, если есть, нужны в simple-launcher MVP?]`).
- **OUT-011**: Theme как отдельный bundled JSON kind — пока ui-pool entry со choices (`light`, `dark`, `auto`). Marketplace тем — отдельная задача (TASK-35).

---

## Assumptions

### Зависимости от других задач

- **A-1**: TASK-1 (F-3 Wizard Module + Localization) — **Done**. Все ports, engine, pool'ы, Senior UI primitives, локализация infrastructure доступны.
- **A-2**: Исторический spec 010 (setup-assistant) — orphan по backlog'у, но **код в репозитории есть**: `SetupCheck.kt`, `SetupCheckEngine.kt`, `RoleHomeCheckAdapter.kt`, `ConfigEditor.kt`, `SetupChecksBadge.kt`. TASK-7 опирается на эту инфраструктуру для skip-with-banner.
- **A-3**: Исторический spec 003 (UI skeleton) — orphan по backlog'у, код частично есть (`HomeActivity` через app/). TASK-7 проверяет что renderer работает с применённой композицией.
- **A-4**: Spec 007 (pairing-and-firebase-channel) — `LinkRegistry.kt` в коде. Pairing UI — `[NEEDS CLARIFICATION: status — Done or partial? если partial, optional QR step в simple-launcher manifest — stub или отсутствует]`.

### Архитектурные принципы

- **A-5**: Profile-as-composition per constitution Article VII §9–13. TASK-7 — первая валидация модели на concrete profile'е.
- **A-6**: LOCAL mode device self-sufficiency per decision 2026-06-15-deferred-cloud. Wizard работает без сети.
- **A-7**: Personas — assisting (родственник / помощник / IT-support), not primary user. Senior-safe baseline всё равно держится для primary user'а кто будет потом устройством пользоваться.
- **A-8**: Wire-format kinds текущего поколения зафиксированы в Article VII §10. Любое изменение — schemaVersion bump per CLAUDE.md rule 5.

### Технические допущения

- **A-9**: Эмулятор `pixel_5_api_34` через skill `android-emulator` — primary local test path (избегаем compose UI test API 35+ blocker).
- **A-10**: `BundledConfigSource` (в `:app`) уже умеет читать assets — TASK-7 не модифицирует.
- **A-11**: `AndroidSystemSettingAdapter` уже handles ROLE_HOME / POST_NOTIFICATIONS / accessibility / battery / hide-status-bar mechanisms — TASK-7 не модифицирует.
- **A-12**: `StringResolver` + `LocaleProvider` уже работают per ADR-004 — TASK-7 только добавляет string entries.

---

## Local Test Path *(mandatory)*

- **Emulator / device**: `pixel_5_api_34` через skill `android-emulator` (memory `reference_compose_ui_test_api_mismatch.md` — избегаем API 35+).
- **Fake adapters used**:
  - `FakeConfigSource` (F-3 commonTest) — заменяет `BundledConfigSource` для unit tests; конструируется in-memory с simple-launcher manifest + assets.
  - `FakeSystemSettingAdapter` (F-3 commonTest) — заменяет AndroidSystemSettingAdapter; tests manifest replay через симулированный пермишн grant/deny.
  - `RecordingDiagnosticEmitter` (F-3) — записывает события для assertions.
  - `FakeLocaleProvider` (F-3) — override locale для locale-switching tests.
  - `InMemoryCheckpointStore` (F-3) — wizard checkpoint persistence без DataStore.
- **Fixtures / seed data**:
  - Bundled JSON в `core/src/androidMain/assets/wizard/`: `wizard-manifests/simple-launcher.json` (заполненный), `screen-layouts/*.json`, `tile-sets/*.json`. Production-ready.
  - `core/src/commonTest/resources/fixtures/simple-launcher-v1-fixture.json` — golden fixture для roundtrip / backward-compat тестов.
- **Verification command**:
  - Unit / contract: `./gradlew :core:test --tests *SimpleLauncher*` + `./gradlew :core:test --tests *RoundtripTest*` + `./gradlew :core:test --tests *BackwardCompatTest*`.
  - Fitness functions (Konsist): `./gradlew :core:test --tests *Task7ArchitectureTest*` — verify no new modules, no `appFamilyId == "simple-launcher"` branches, no new ConfigKind entries.
  - Android instrumented: `./gradlew :app:connectedDebugAndroidTest --tests *SimpleLauncherE2ETest*`.
  - Emulator smoke (через skill `android-emulator`): `./gradlew :app:installDebug` → launch via adb → manual walkthrough.
- **Cannot-test-locally gaps**:
  - **Real ROLE_HOME OEM-specific dialogs** (Samsung One UI confirm, Xiaomi MIUI quirks) — inline TODO(physical-device); fake adapter покрывает только baseline AOSP.
  - **Real elderly walkthrough** (low vision, reduced dexterity, no tech literacy) — `[hand]` AC через skill android-emulator + senior-safe checklist; не automated.
  - **System locale change persistence** через OEM Settings UX — baseline тестируется на эмуляторе, edge cases inline TODO(physical-device).

---

## AI Affordance *(mandatory)*

Wizard сам по себе не содержит AI-specific surfaces в TASK-7 scope. Capability Registry forward-pointer:

- **Exposable capabilities** (future, через Capability Registry, TASK-33 deferred): `runWizardStep(stepId)` (re-run specific step через voice / AI assistant), `applyTileSet(tileSetId)`, `setTheme(themeId)`, `setLocale(localeTag)`. Все — domain verbs.
- **Required affordances on data**: read-only access to `UserPreferences`, `WizardOutcome.Completed`. Никакой PII не leaves device.
- **Provider-agnostic shape**: capabilities expressed через existing F-3 domain ports, без Gemini/OpenAI/Claude/MCP types (CLAUDE.md rule 1).
- **Out of scope for this spec**: AI provider implementation, LLM prompt design, telemetry — TASK-36 / FUTURE-SPEC-AI.
- **Inline TODO**: `// TODO(capability-registry): wizard capabilities expose via Capability Registry adapter — F-2 deferred to end of Phase 2 per roadmap reorder 2026-06-15`.

---

## OEM Matrix *(mandatory if feature touches device behavior)*

Wizard touches: `ROLE_HOME` request (RoleManager), `POST_NOTIFICATIONS` permission (Android 13+), system locale read, DataStore persistence, Settings deep-links.

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---|---|---|---|
| Stock Android (Pixel) | baseline | — | emulator `pixel_5_api_34` через skill `android-emulator` |
| Samsung One UI | ROLE_HOME picker может добавлять confirm dialog после нашего request'а | `SystemSettingPort.status()` re-check после возврата фокуса; inline TODO(physical-device) | TODO(physical-device) — Samsung Galaxy newer |
| Xiaomi MIUI | autostart manager + battery optimization (но FCM / background out-of-scope для TASK-7); ROLE_HOME baseline | inline TODO(physical-device) для autostart; ROLE_HOME проверяется baseline | TODO(physical-device) — Xiaomi 11T |
| Huawei EMUI (GMS-less) | spec 010 FR-042 hard-block **до** wizard'а — TASK-7 wizard не запускается | Delegation to spec 010 hard-block path | spec 010 FR-042 + код `GmsAvailabilityPort` |
| Stock Android API < 13 | `POST_NOTIFICATIONS` не существует — auto-skip step через `androidMinApi: 33` в pool | F-3 engine handles | emulator `pixel_5_api_31` regression test |

---

## Cross-cutting concerns surfaced from architectural model

1. **Constitution amendment 1.7 alignment**: TASK-7 — первая spec'а написанная **после** amendment'а 1.7 (Article VII §9–13). Валидирует модель «profile = composition, not code» на concrete profile'е.
2. **Wire-format kinds эволюция (Article VII §10)**: TASK-7 задействует существующие пять (wizard.manifest, screen.layout, tile.set, system-settings.pool, ui-customization.pool), не вводит новые. Если в plan'е выяснится что для simple-launcher не хватает kind'а — это поднимается как exception с обоснованием.
3. **Per-profile override (Article VII §12)**: TASK-7 валидирует mechanism per-step `canSkip` / `criticality` override в manifest'е поверх pool defaults. Минимум один override (ROLE_HOME canSkip=false для simple-launcher).
4. **No per-profile code module (Article VII §13)**: фитнесс-функция проверяет что TASK-7 не добавляет dedicated Gradle module + не добавляет `if appFamilyId == "simple-launcher"` branches.
5. **Skip-with-banner integration**: TASK-7 опирается на `SetupCheckRegistry` (spec 010 orphan но код есть). Не дублирует механизм.
6. **Senior-safe baseline (Article VIII §7)** держится несмотря на то что actor — assisting; primary user может потом сам взаимодействовать.

---

## Затрагиваемые внешние артефакты

- **TASK-1 (F-3) bundled assets** — `core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json` MUST быть заполнен явными steps. Это **модификация существующего файла**, а не создание нового.
- **`core/src/androidMain/assets/wizard/screen-layouts/`** — могут быть добавлены новые JSON'ы (`2x3-classic.json`, `4x5-classic.json`) если параметризованный layout недостаточен (clarify Q).
- **`core/src/androidMain/assets/wizard/tile-sets/`** — могут быть добавлены новые tile.set'ы (clarify Q).
- **String resources** (`core/src/commonMain/composeResources/values/strings.xml` + `values-ru/strings.xml`) — новые entries для simple-launcher specific keys.
- **`docs/dev/server-roadmap.md`** — inline TODO про future NetworkConfigSource (уже есть от F-3).
- **TASK-1 backlog file** — потенциально mark related: «TASK-7 confirmed F-3 contract works for first concrete profile».

---

## Открытые вопросы для `/speckit.clarify`

Сводка `[NEEDS CLARIFICATION]` маркеров (для удобства следующего шага). **Заметно меньше чем в первой версии** — архитектурные вопросы закрыты constitution amendment 1.7.

1. **Финальный список steps + порядок в simple-launcher.wizard.manifest.json** (FR-002). Минимум: language, ROLE_HOME, POST_NOTIFICATIONS, theme/grid/tileSet. Точный порядок + какие optional vs mandatory — фиксируется.
2. **Per-step `canSkip` override list** (US-2 #5, FR-003). Минимум ROLE_HOME override. Что ещё?
3. **screen.layout — один параметризованный или несколько bundled под каждый grid choice?** (FR-005). Сейчас один 3x4-classic; нужны ли 2x3 / 4x5 как отдельные документы или достаточно `grid` UI option на параметризованном layout'е?
4. **Tile.set variants** (FR-006). Сейчас один classic-6. Нужны ли additional (classic-9, classic-12, или сегмент-specific) в TASK-7 scope, или этого достаточно для MVP?
5. **App-level locale override vs системная локаль Android** (US-4, edge case): persist user locale choice через `AppCompatDelegate.setApplicationLocales()` (override) или follow system locale (no override)?
6. **Theme editing post-wizard** (OUT-009): отдельный Settings entry в TASK-7 scope, или только через wizard / banner re-run?
7. **Tutorial hints для simple-launcher MVP** (OUT-010): какие конкретные hints через `TutorialHintManager` (если вообще)?
8. **Bundled JSON corruption на disk UX path** (Edge cases): UI error «не могу прочитать профиль» с retry, или fallback на minimal layout?
9. **QR pairing optional step в manifest** (OUT-008, A-4): stub button с deep-link на Settings, отсутствует совсем, или реальная интеграция?
10. **`simple-launcher` profile id vs `appFamilyId` field name confusion** — manifest сейчас имеет `appFamilyId: "simple-launcher"` (wire-format field name), а в спеках / docs мы говорим «profile id `simple-launcher`». Подтвердить что field name retain'ится для backward compat (per Article VII §9, glossary §2) — это **не** open question, это decision, но нужно явно зафиксировать в plan.md.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** TASK-7 = ship `simple-launcher` профиль (constitution Article VII §9–13) как композицию bundled JSON-документов поверх существующего wizard engine из TASK-1 (Done). Это **content authoring + integration**, не infrastructure. Spec реализует Article VII §10 fixed wire-format kinds и Article VII §12 mandatory/optional/skip semantics через pool defaults + per-profile override на конкретном профиле.

**Конкретика, которую стоит запомнить:**
- **TASK-7 не вводит новый Kotlin-код** (FR-022–025): нет новых Gradle модулей dedicated for simple-launcher, нет `if (appFamilyId == "simple-launcher")` branches, нет новых `ConfigKind` enum entries, нет новых ports.
- **Deliverables**: (a) заполненный [`simple-launcher.wizard.manifest.json`](../../core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json) с явными `steps[]` вместо `autoOrder: true`; (b) возможные новые bundled `screen.layout` / `tile.set` JSON'ы; (c) en + ru локализация под все ключи; (d) integration tests + senior-safe walkthrough.
- **Минимум один per-step override**: `android.role.home` с `canSkip: false` (pool default `true`) — для simple-launcher без ROLE_HOME продукт бессмыслен.
- **Existing pool entries задействованные**: `language` (Required), `theme` (Optional), `grid` / `screenLayout` / `tileSet` (mix), `android.role.home`, `android.permission.POST_NOTIFICATIONS`. Возможно `android.permission.CALL_PHONE` (Optional).
- **Actor wizard'а**: `assisting family member / помощник / IT-support`, не primary user. Но senior-safe baseline (Article VIII §7: ≥ 56dp tap targets, ≥ 24sp text, ≥ 7:1 contrast) держится поскольку primary user может позже сам взаимодействовать.
- **Объём scope**: 6 US (P1×2 / P2×2 / P3×2), 25 FR в 5 частях (A-E), 11 SC (7 c `[backlog]`), 11 OUT-OF-SCOPE, **9 open `NEEDS CLARIFICATION`** для `/speckit.clarify` (значительно меньше чем в первой неправильной версии — архитектурные вопросы закрыты Article VII §9-13).
- **Effort**: Medium (~1-2 weeks), не Large (~3 weeks как в исходной task description). Infrastructure уже есть.

**На что смотреть с осторожностью:**
- **`appFamilyId` wire-format field name** retain'ится как deprecated synonym для «profile id» (glossary §2, Article VII §9). В новом коде / docs использовать «profile id»; в JSON / pre-existing wire format — `appFamilyId` без изменений. Не путать.
- **Зависимости spec 010 / spec 007 / spec 003 orphan**: код есть, но статус задач не подтверждён в backlog'е (нет ссылок). Если в plan'е выяснится что какая-то orphan-функциональность не работает или partial — TASK-7 может задеплоить broken assumption. Mitigation: integration tests на every dependent path.
- **`SetupCheckRegistry` skip-with-banner integration**: spec 010 orphan; если код выпрямлен или несовместим — TASK-7 FR-015 может потребовать workaround / fix.
- **Constitution amendment 1.7 alignment fitness function**: SC-010 требует Konsist test verify'ущий что нет regressions в model (нет dedicated module / branch / new kind). Это новая automation, не сложная, но требует написания.
- **Senior-safe walkthrough — `[hand]` AC**: SC-007 не автоматизируется. После merged PR может остаться `[ ]` → backlog в Verification, не Done.
