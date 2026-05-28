# 03. Launcher UI & Accessibility — то, что видит Managed

> **Status**: 🟡 partially decided (D-5, D-7, D-8 RESOLVED 2026-05-27 evening) · **Created**: 2026-05-27

## Setup Wizard — primary entry point (D-5, D-7, D-8 unified resolution)

> Решение 2026-05-27 evening: Setup Wizard становится **обязательным entry point** Simple Launcher preset. Никогда не показываем empty main screen — wizard завершает critical config **до** показа главного экрана.

### Зачем

Пугать пользователя пустым экраном **нельзя**. Бабушка не должна видеть «Загрузка...» или «здесь ничего нет». На первом запуске — **wizard**, который ведёт через все critical steps. После wizard — main screen с реальным контентом.

### Wizard phases

**Phase 1 — Critical (mandatory)**:
1. Welcome screen (большой шрифт, простой язык).
2. ROLE_HOME grant (через `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)`).
3. POST_NOTIFICATIONS Android 13+.
4. Default text size + theme (warm-contrast light / dark).

**Phase 2 — Setup choices (skippable)**:
5. Grid preset selection: «Простой» 2×3 / «Обычный» 3×4 / «Насыщенный» 4×5 (default: Простой).
6. Pairing с admin'ом (QR scan) или «настрою позже».
7. Initial contact tiles (либо local entries, либо «admin настроит удалённо»).

**Phase 3 — Tutorial / accessibility hints (optional)**:
8. Tutorial с подсказками: «как пользоваться», «где искать настройки».
9. Mention available accessibility settings (text-size adjust, contrast theme, touch debounce). NOT toggles для unimplemented features (dwell — пока inline TODO).

### Checkpoints & resumability

Wizard сохраняет state после каждого шага. Если прерван (закрыли app, перезагрузка) — продолжается с того же шага.

Skipped optional steps доступны позже из Settings.

### Что закрывает

- **D-5**: top-level empty state не существует — wizard enforce'ит config completion.
- **D-7**: grid preset выбирается в wizard'е, default 2×3 (Wiser pattern).
- **D-8**: dwell-to-activate — НЕ показываем toggle в MVP wizard (не врём про availability). Post-MVP, когда implementation готов.

### Component-level states (cross-spec rule остаётся)

**Не путать**: top-level empty не существует, но **внутри** components всё ещё могут быть состояния. Cross-spec rule в `design-system.md`:

- **Loading skeleton** для отдельного тайла во время data fetch (миллисекунды).
- **Missing app**: grayed icon + «не установлено» badge + tap → Play Store deep link.
- **Error**: tile с red-dot badge + tap → диалог с описанием.

`checklist-ux-quality` обновляется:
- Новые user-facing flows должны различать 4 component-level states.
- Новый пресет должен иметь wizard, который предотвращает empty-at-launch.

### Wizard как самостоятельный модуль (refined 2026-05-27 evening)

**Архитектура**:

```
core/wizard/                      ← independent reusable module
├── WizardEngine                  ← state machine, orchestration
├── WizardStep (interface)
├── WizardManifest                ← preset's declaration of needed steps + templates
├── ConfigTemplate                ← pure data: tile arrangement + widgets
└── steps/                        ← reusable step library
    ├── PermissionStep            (с deep-link через Settings.ACTION_*)
    ├── TextSizeStep
    ├── ThemeStep
    ├── GridSelectionStep
    ├── PairingStep
    ├── ConfigTemplatePickerStep
    └── TutorialHintStep

presets/<preset>/
├── <Preset>WizardManifest        ← preset-specific wizard composition
└── templates/                    ← preset-specific config templates (pure data, JSON)
    ├── template-a.json
    ├── template-b.json
    └── ...
```

**Свойства**:
- WizardEngine — preset-agnostic. Каждый preset реализует свой Manifest.
- Steps — переиспользуемые между preset'ами (PermissionStep одинаковый для всех).
- Templates — pure data, **не код**. Готовые конфигурации внутри preset'а: «8 плиток + календарь снизу» / «12 плиток без виджетов» / «6 плиток + emergency banner». Пользователь выбирает в wizard'е.
- **Wizard vs Tutorial** — разные концепции в одном module. Wizard = one-time setup. Tutorial = contextual ongoing hints.

**Templates — shareability-ready per CLAUDE.md rule 9**:

Templates обязательно проектируются как **portable shareable artifacts**, даже если sharing UI не строится сейчас. Конкретно:

- **Wire-format с `schemaVersion`** (CLAUDE.md rule 5 + 9).
- **Обезличенный** контент: только tile **types** + positions + widget choices, **без** contact identifiers, без photo blob references, без UID'ов. Template — «структура раскладки», не «раскладка конкретного user'а».
- **`ConfigSource` adapter pattern**:
  - `BundledConfigSource` — для shipped-with-app templates (один из source'ов, не единственный).
  - Future: `ImportFromFileConfigSource`, `NetworkConfigSource`, `ShareIntentConfigSource`, `MarketplaceConfigSource`.
  - При импорте нового template из любого source — тот же `WizardEngine`, тот же `ConfigTemplate` тип.
- **Inline TODO** у `BundledConfigSource`: `// TODO(shareability): future sources — file import, share intent, curated marketplace`.
- **Roundtrip + cross-device test** с первого коммита.

**Это НЕ требует** строить sharing UI / marketplace сейчас. **Это ОБЕСПЕЧИВАЕТ**: добавление sharing позже = новый `ConfigSource` adapter, без переписывания template format.

**MVP scope для wizard module**:
- WizardEngine + 6-8 переиспользуемых steps.
- 2 Manifest'а: Simple Launcher + Admin App.
- 2-3 starter templates для Simple Launcher, 1 для Admin App — через `BundledConfigSource`.
- `ConfigSource` adapter interface готов; только Bundled implementation реализуется.

**Post-MVP**:
- TV preset Manifest (когда D-24 раскрывается).
- Caregiver preset Manifest.
- `ImportFromFileConfigSource` — пользователь импортирует template из shared file.
- `MarketplaceConfigSource` — curated community templates.
- Dynamic template download без app update.

### Что нужно сделать (action items)

- **Расширить спеку 010** (Setup Assistant) до полноценного wizard ИЛИ написать новую `013-wizard-framework` + `014-simple-launcher-wizard-manifest` (lean toward two separate specs — engine vs preset-specific manifest).
- **Поднять FUTURE-SPEC-006** (onboarding-and-tutorials) из 🔮 в must-have для MVP — становится частью wizard module.
- **Создать reference**: список Android Settings deep-links (`Settings.ACTION_HOME_SETTINGS`, `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, etc.) для каждого permission step.
- **Templates как wire-format**: применить CLAUDE.md rule 5 (`schemaVersion`, roundtrip-test, backward-compat).


> **Зачем читать**: это **главный экран продукта** глазами пожилого пользователя. Сюда сходится вся работа из остальных доменов — здесь видно, что реально получилось. Все «загрузка...» / empty state / accessibility-проблемы — здесь.
> **Источник**: `user-journeys-draft.md` §5 (паттерны лаунчеров) + §6 (accessibility).

---

## Что это за документ (просто)

Это **поверхность продукта** — то, что Managed user видит каждый день. Сюда конвертируется всё, что произошло в pairing'е, в config sync, в encryption — на бабушкином экране пользователь видит либо красивую и удобную плитку, либо «Загрузка…» (и это уже наш fail).

Документ обсуждает **визуальную модель** (как выглядит главный экран), **interaction model** (как с ним взаимодействуют), и **accessibility** — что должно быть учтено, чтобы не сделать продукт неюзабельным для нашей целевой аудитории.

Принципиально важно: **локальный edit UI = remote admin UI**. Не разрабатываем два UI. Это решает D-2 и закрывает боль S-105/S-801 (тестировщик не может локально). См. user-journeys-draft.md §5.4 рекомендация 1.

## Главные понятия (просто)

- **Home screen (главный экран)** — то, что Managed видит по нажатию HOME. У нас — набор плиток (Tile) в сетке (Grid).
- **Tile (плитка)** — одна интерактивная ячейка на экране. Может быть контактом / приложением / SOS / website. У плитки есть **состояния**: filled (есть содержимое), empty (нет содержимого), loading (грузится), error (ошибка), missing (приложение удалили).
- **Edit mode (режим редактирования)** — состояние, в котором можно перемещать / добавлять / удалять плитки. Включается долгим нажатием или через admin-gate (7-tap из спека 010).
- **Empty state (пустое состояние)** — что показываем, когда **нет данных**. **Не «Загрузка...»**, а осмысленный текст с подсказкой, что сделать.
- **Loading state (состояние загрузки)** — что показываем, **пока данные грузятся**. Это **отдельное** состояние от empty.
- **Error state (состояние ошибки)** — что показываем, когда что-то конкретно сломалось.
- **Senior-safe** — наш сленг для accessibility-усиленных правил (>56dp targets, touch debounce, и т.п.).
- **Accessibility domain (домен доступности)** — одна из 6 областей: зрение, моторика, когнитив, слух, голос, confidence. Каждый со своими правилами.

## Use case инвентарь (UI-specific)

### Состояния тайла

| ID | Случай | Что показываем | Status |
|---|---|---|---|
| UI-001 | Тайл с контактом, фото есть | photo + name под ним | 🟡 (012 stub) |
| UI-002 | Тайл с контактом, фото нет | initials в круге (или generic icon) + name | 🟡 |
| UI-003 | Тайл с приложением | app icon + label | ✅ (003) |
| UI-004 | Тайл загружается | **skeleton placeholder** (контур плитки + пульсация), НЕ текст «Загрузка...» | ❌ |
| UI-005 | Тайл пустой (admin не добавил) | (в normal mode) — пусто; (в edit mode) — «+» в центре | ❌ (D-5) |
| UI-006 | Тайл missing app (приложение удалено) | grayed icon + badge «не установлено» + tap → Play Store | ❌ |
| UI-007 | Тайл с error (например, contact unsynced) | icon + small red dot + tap → объяснение | ❌ |
| UI-008 | Тайл SOS | red большой, по центру нижней половины (one-handed reach) | ❌ |
| UI-009 | Тайл с live data (пропущенные, badge) | photo + badge | 🔮 Square Home pattern |

### Главный экран

| ID | Случай | Что показываем | Status |
|---|---|---|---|
| UI-101 | Главный с N тайлами (N ≤ 6) | grid 2×3 или 3×2 | 🟡 |
| UI-102 | Главный пустой (admin ещё не настроил) | empty state с CTA «попроси admin'а...» или «Add tile» (в edit mode) | ❌ |
| UI-103 | Главный с пропущенным звонком (badge на тайле) | badge с количеством | ❌ |
| UI-104 | Главный с offline-индикатором | banner «нет связи» вверху | ❌ (deferred → 013) |
| UI-105 | Главный с уведомлением «новая фотография от внука» | snackbar или banner | ❌ |
| UI-106 | Главный в high-contrast theme | warm-side colors, не cool blue | ❌ |
| UI-107 | Главный с увеличенным шрифтом (1.5×) | размер тайлов адаптируется | ❌ |

### Edit mode

| ID | Случай | Что показываем | Status |
|---|---|---|---|
| UI-201 | Вход в edit mode (7-tap admin gate) | вибрация эскалирует, потом UI меняется (outlined cells + «+» в пустых) | ✅ (010) |
| UI-202 | Перемещение тайла | drag-drop или Android 15 picker → place | ❌ |
| UI-203 | Добавление тайла | picker «Контакт / Приложение / SOS / Веб» | ❌ |
| UI-204 | Удаление тайла | long-press → «удалить» с Undo snackbar 5 sec | ❌ |
| UI-205 | Выход из edit mode | tap «Готово» / Back / tap вне | ❌ |

### Accessibility scenarios

| ID | Случай | Поведение | Status |
|---|---|---|---|
| UI-301 | TalkBack включён | каждая плитка имеет `contentDescription` как команду («Позвонить внуку») | ❌ |
| UI-302 | Magnification triple-tap | system handles, мы не мешаем | ❓ |
| UI-303 | System font scale 1.5× | наш UI адаптируется (не обрезает текст) | ❌ |
| UI-304 | High contrast mode (system) | наш theme респектит system setting | ❌ |
| UI-305 | Voice Access («open WhatsApp tile») | работает (нужен правильный contentDescription) | ❌ |
| UI-306 | Switch Access (внешний switch hardware) | works (TalkBack-совместимость = большая часть compatibility) | ❌ |
| UI-307 | Dwell-to-activate (для сильного тремора) | опциональная фича: hover ≥ 1 sec = tap | 🔮 |
| UI-308 | Touch debounce (повторные тапы) | 300ms игнор повторных тапов на ту же зону | ❌ |

## Главные открытые вопросы

### D-5. Empty / Loading / Error / Missing states — cross-spec rule

**Контекст**: текущая боль user'а — «пустая раскладка показывает 'Загрузка...'». Это **не bug одной спеки**, это **отсутствие cross-spec правила**.

**Что предлагаем зафиксировать как правило** (`docs/dev/design-system.md`):
- **Loading state** = skeleton placeholder (контур плитки + lottie/shimmer). Видим **только пока реально грузим**.
- **Empty state** = пустота (в normal mode) или «+» (в edit mode). Если empty потому что admin не настроил — banner «попроси admin'а настроить» под главным.
- **Error state** = плитка с red-dot badge + tap → диалог с описанием.
- **Missing app** = grayed icon + badge + tap → install prompt.

**One-way door**: введение правила сейчас означает, что все будущие спеки **обязаны** показывать 4 разных состояния. Это **architectural rule**, не «совет».

**Регрет-условие**: если оставить как есть — каждая будущая фича повторит ту же ошибку.

**Рекомендация**: **зафиксировать как cross-spec rule** в `design-system.md` и обновить `checklist-ux-quality` чтобы проверял на каждой спеке.

### D-7. Default tile limit (6 ± 2) — навязывать или нет

**Контекст**: Wiser использует 6 тайлов на главной — это снижает cognitive load. Но 6 может быть мало для admin'а («хочу 12 контактов»).

**Варианты**:
- **Жёсткий лимит 6**: senior-safe by default. Минус — admin недоволен.
- **Soft hint, default 6, можно до 12 без warning**: гибко. Минус — admin может ставить 20 и теряет senior-safety.
- **Configurable per Managed device — senior-safe / standard / dense**: при первичной настройке выбираем «как простой» / «как обычный» / «как насыщенный».

**Рекомендация (best-guess)**: вариант 3 (configurable preset). При first-run wizard'е admin выбирает preset, потом можно поменять. **Default = senior-safe (6)**. Это закрывает D-7.

### D-8. Dwell-to-activate — в core или нишево

**Контекст**: dwell-control (наведи и подержи 1 сек = tap) — для людей с сильным тремором. Полезно ≈3% юзеров.

**Варианты**:
- **В MVP**: каждый user получает опцию. Минус — extra dev work, может багнуть обычное поведение.
- **Inline TODO**: помечаем код, но реализуем позже.
- **Out of scope**: полагаемся на system AssistiveTouch (iOS) / system Switch Access (Android).

**Рекомендация**: inline TODO. Architecturally подготавливаем (target halo + touch debounce уже подготавливают почву), но dwell-mode UI — позже.

### D-Local. Local edit UI vs remote-only edit UI

**Контекст**: см. D-1 (companion vs self-serve) и user-replica «удалённая настройка = та же настройка». Если local edit UI = remote admin UI (один компонент) — это **снимает боль S-105 (тестировщик)** + **открывает D-1 self-serve без отдельной кодовой базы**.

**Варианты**:
- **One UI**: local edit и remote admin — один компонент, parameterized acting actor. **Рекомендуется.**
- **Two UIs**: separate codepaths. Дороже, дублирование.

**Рекомендация**: **One UI**. Это закрывает D-2 в README.

### D-Naming. Avoidance senior-стигмы

См. D-6 в 01-vision. UI не должен **выглядеть** «для стариков» — иначе целевые «молодые пожилые» 60-70 лет отвергнут.

**Рекомендация**: следовать Material 3 / iOS native визуальному языку. Просто **крупнее**, **проще**, **stable**. Не «retro-skin» как BIG Launcher.

## Что в спеках уже зафиксировано

| Спек | Что фиксирует |
|---|---|
| 001 Launcher Core | базовое поведение HOME, ownership boundaries |
| 003 UI Skeleton | базовая UI структура (flow, slot, settings) |
| 004 UI Stack Migration | Compose Multiplatform + Material 3 + Decompose + Koin |
| 005 Action Architecture v2 | wire format actions; провайдеры через ProviderRegistry |
| 006 Provider Capabilities | health snapshot, capability detection |
| 010 Setup Assistant | 7-tap admin gate, ROLE_HOME, custom call confirmation, soft-checks |
| 011 Crypto Foundation | архитектурная база для photo-tiles |
| 012 (stub) Contact Photos | photo on tile UX |

## Связь с другими документами

- **02 Actors** — UI делается для Managed (A1) и через Bystander (A4) — должен быть очевиден без обучения.
- **04 Remote management** — admin использует **тот же** edit UI, что и local-edit, только с remote source of truth. Один компонент.
- **05 Pairing** — что показывать, пока не paired (empty state) — здесь.
- **06 Communications** — как выглядит тайл звонка / видео — здесь.
- **07 Data & privacy** — что отображать или не отображать с privacy-tags — здесь.

## Принципы дизайна (assembly из user-journeys-draft.md §6.4)

### Визуальные правила
- Базовый шрифт = `max(1.0, system fontScale × 1.2)`, capped 1.5×.
- 2 темы: high-contrast light / high-contrast dark.
- Action color — тёплый (orange / green / amber), **не** cool blue (желтение хрусталика пожилых).
- Action в центре экрана, decoration по краям (узкое поле зрения).
- Photo-first для контактов; текст под фото, не вместо.
- Target ≥ 56dp; spacing ≥ 16dp между тайлами.

### Motor правила
- Touch debounce 300ms.
- Target halo +8dp за визуальным краем.
- Edge-of-tile 5dp buffer-zone между тайлами (анти-промах).
- Long-press timeout configurable (default 500ms).
- Опция dwell-to-activate (inline TODO).

### Cognitive правила
- Max 2 уровня меню (главный → действие).
- Default 6 ± 2 тайла на экран (D-7 configurable preset).
- Стабильное положение тайлов — нет smart-sort.
- Single back, всегда видим. Home → главный экран.
- Snackbar Undo 5 sec для reversible. Confirmations только для irreversible.

### Слух / голос
- Notification = visible + haptic + (optional) audio. **Никогда audio-only.**
- TalkBack / Voice Access совместимость mandatory.

### Confidence
- Haptic + ripple на каждый успешный tap.
- Skeleton для loading, empty-state-with-CTA для нет-данных. **Никаких «Загрузка...» где данных просто нет.**

### Anti-patterns (НЕ делаем)
- Замена системных Phone/SMS своими — никогда.
- Senior-стигма в брендинге.
- Password gate без recovery (наш 7-tap + rotating challenge лучше).
- Cool blue action colors.
- Smart-sort by usage на главной.

## Источники

См. `user-journeys-draft.md` §9 — там полный research-bench:
- BIG Launcher Manual, Wiser, Necta, Square Home, Niagara reviews
- Android 15 widget placement, iOS jiggle mode reference
- Touch Guard (ACM W4A 2015) — 65% tremor error reduction
- 2025 academic systematic review на elderly UX (PMC)
- iOS Dwell Control reference
- TalkBack / Voice Access docs

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |
