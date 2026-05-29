# Project Constants — Already-Decided Things (DO NOT RE-DISCUSS)

**Purpose**: prevent endless re-asking of decisions that are already made and implemented. Future agents and reviewers MUST check this file before raising "design questions" about these topics.

If you (agent or human) are about to ask the user "how should X work?" — first grep this file. If X is here, the answer is here.

---

## Семь-тап жест для входа в admin/edit-mode

**Status**: ✅ IMPLEMENTED (specs 009, 010 FR-021)

**Где живёт**:
- Logic: [`core/src/commonMain/kotlin/com/launcher/ui/gate/SevenTapDetector.kt`](../../core/src/commonMain/kotlin/com/launcher/ui/gate/SevenTapDetector.kt)
- Modifier: [`core/src/commonMain/kotlin/com/launcher/ui/gate/SevenTapGateModifier.kt`](../../core/src/commonMain/kotlin/com/launcher/ui/gate/SevenTapGateModifier.kt)
- Attachment point: [`HomeScreen.kt:58`](../../core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt#L58) — `Modifier.sevenTapAdminGate(...)`
- Spec: [`specs/010-setup-assistant/spec.md`](../../specs/010-setup-assistant/spec.md) FR-021

**Поведение**:
- Цель: 7 быстрых тапов в течение **5 секунд** в радиусе **±48dp** по обеим осям от первого тапа.
- Где можно тапать: **пустое пространство между BottomFlowBar (внизу) и плитками** на home screen. НЕ по плиткам, НЕ по BottomFlowBar.
- Haptic feedback escalation:
  - Тапы 1-3: лёгкая вибрация (`HapticFeedbackType.TextHandleMove`).
  - Тапы 4-6: средняя (`HapticFeedbackType.LONG_PRESS`).
  - Тап 7: success pattern + триггер.
- При success: `onSevenTapTriggered()` → `RootComponent.nav.push(RootConfig.ChallengeGate)` → после успешной challenge → admin mode.

**Применяется в**:
- **Simple Launcher (бабушкин телефон)** — единственный способ войти в edit-mode локально на её устройстве. До 7-tap бабушка видит home в **режиме использования** (senior-safe UX); после 7-tap → challenge gate → admin/edit-mode.
- **Workspace (admin телефон)** — тоже работает, хотя у admin'а есть и другие способы войти в edit (long-press, явная кнопка).

**Что НЕ ставить под сомнение**:
- Не предлагать «может, лучше 5 тапов?» — 7 это конвенция Android (Build Number → developer options).
- Не предлагать другие места привязки (на иконку настроек, на logo и т.д.) — пустое место home screen зафиксировано.
- Не предлагать timeout другой длины — 5 секунд протестировано.
- Не предлагать модифицировать tolerance — ±48dp учитывает дрожание руки пожилого.

---

## Workspace / Flow tab navigation — BottomFlowBar

**Status**: ✅ IMPLEMENTED (специка 003 UI skeleton)

**Где живёт**:
- [`core/src/commonMain/kotlin/com/launcher/ui/components/BottomFlowBar.kt`](../../core/src/commonMain/kotlin/com/launcher/ui/components/BottomFlowBar.kt)
- Подключён в [`HomeScreen.kt`](../../core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt)

**Поведение**:
- Внизу экрана — горизонтальный bar с tabs (по одному на каждый Flow в текущем preset'е).
- Тап по tab'у → активный Flow меняется, рендерится соответствующий `FlowScreen`.
- В Workspace preset'е admin может иметь N tabs (например: «Контакты», «Управление устройствами», «Документы»).
- В Simple Launcher (senior) обычно 1-2 tabs.

**Что НЕ ставить под сомнение**:
- Не предлагать swipe между Flow'ами как primary navigation — bottom bar это явная видимая навигация, более discoverable.
- Не предлагать top tabs или side drawer — bottom bar уже выбран и работает.

---

## Flow Templates (типизированные workspace)

**Status**: ✅ PARTIALLY IMPLEMENTED (спека 003 + расширение в будущей 014)

**Где живёт**:
- [`core/src/commonMain/kotlin/com/launcher/api/FlowModels.kt`](../../core/src/commonMain/kotlin/com/launcher/api/FlowModels.kt) — `FlowTemplate`, `FlowDescriptor.templateId`.
- [`core/src/commonMain/kotlin/com/launcher/api/FlowRepository.kt`](../../core/src/commonMain/kotlin/com/launcher/api/FlowRepository.kt) — `availableTemplates(presetId)`.

**Концепция**:
- Каждый Flow (вкладка) имеет `templateId` — определяет **тип workspace**.
- Текущие type'ы (по состоянию 2026-05-28): `"contacts"`, `"admin_devices"`.
- Спека 014 расширяет: тип определяет **что можно добавить** через `+` и **какие предзаполненные плитки** есть при создании Flow.
- Пример типов в планах: `"universal"` (любые плитки), `"contacts"` (только контакты), `"admin_devices"` (только сопряжённые устройства), `"documents"`, `"apps_only"`.

**Важное правило (зафиксировано 2026-05-28)**:
- Target Device — **first-class plate type** в workspace с `templateId="admin_devices"`. Tap → провалиться в editor target'а. Тот же список target'ов доступен через Settings → Сопряжённые устройства; это **два UI входа в одну модель**.

**Что НЕ ставить под сомнение**:
- Не предлагать удалить `templateId` field — уже используется в коде.
- Не предлагать другую схему типизации workspace — `templateId` + `availableTemplates(presetId)` уже работает.

---

## Senior-safe vs mainstream UX rules — РАЗДЕЛЕНИЕ

**Status**: ✅ CLARIFIED 2026-05-28

**Правила**:

| Контекст | UX rules |
|---|---|
| **Simple Launcher в режиме использования** (бабушка пользуется) | Senior-safe — tap-targets ≥56dp, no hidden gestures, explicit feedback, минимум одновременных элементов |
| **Simple Launcher в режиме настройки** (бабушка прошла 7-tap; или admin remote-edit) | Mainstream Android-конвенции — long-press, drag, drop, context menus |
| **Workspace (admin) в режиме использования** | Mainstream — long-press, swipe, etc. (admin tech-savvy) |
| **Workspace (admin) в режиме настройки** | Mainstream + те же edit-affordances |

**Что НЕ ставить под сомнение**:
- Не предлагать senior-safe правила для admin UX — admin'у это не нужно.
- Не предлагать mainstream правила для senior usage-mode — это нарушает senior-safe (отдельная архитектурная константа).
- Senior настройка использует **те же mainstream правила** что и admin — никакого специального senior-safe edit-режима.

---

## Кто может настраивать Simple Launcher

**Status**: ✅ CLARIFIED 2026-05-28

**Правило**: настройка Simple Launcher **не привязана к роли пользователя** и не зависит от того, **с какого устройства** идёт настройка. Доступна:

1. **Локально на телефоне-владельце Simple Launcher'а** — любой человек с физическим доступом к телефону проходит 7-tap → challenge gate → edit mode. Это может быть:
   - **Сама бабушка** (если у неё хватает когнитивных способностей понять 7-tap и UI настройки).
   - **Admin**, временно взявший её телефон в руки.
   - **Любой другой** с физическим доступом (caregiver, родственник).
2. **Удалённо с телефона admin'а** — admin в своём Workspace проваливается в target device (через Settings → Сопряжённые устройства или через вкладку с типом `admin_devices`) и редактирует ConfigDocument бабушки.

Оба пути используют **тот же Editor** (`EditorComponent` + `EditorScreen`), оперируют **тем же ConfigDocument**, сохраняют через **тот же `ConfigEditor` port** (спека 008 bidirectional sync).

**Безопасность**: 7-tap + challenge gate (спека 010 FR-021) защищают от случайных входов в edit mode при чистке экрана / случайных тапов / детских игр с телефоном. Это **достаточно высокий barrier для случайного срабатывания**, но **не cryptographic barrier против целенаправленной атаки физического доступа**. Если у злоумышленника телефон в руках и он знает про 7-tap — он войдёт. Это явное design choice: мы не паролим edit mode, потому что бабушка тоже может захотеть настроить сама.

**Что НЕ ставить под сомнение**:
- Не предлагать «admin'ское настройка через отдельный UI, бабушкина — через другой».
- Не предлагать «бабушка не может настраивать сама» — может, если осилит 7-tap.
- Не предлагать «настраивать может только admin» — может любой с физическим доступом + способностью пройти 7-tap challenge.
- Не предлагать паролить edit mode на бабушкином телефоне — это намеренно открытый barrier, не cryptographic gate.

---

## Progressive Disclosure — multi-X UI hidden until X count > 1

**Status**: ✅ ARCHITECTURAL PRINCIPLE 2026-05-29 (F-014 spec)

**Правило**: Любая user-facing feature, которая работает с **N экземпляров** какой-то сущности (configs, flows, paired devices, admins, etc.), MUST скрывать всю multi-X UI complexity пока у пользователя **только 1 экземпляр**. UI complexity появляется **только** когда `count > 1`.

**Reference industrial patterns**:
- **Chrome Profiles**: 1 profile → no switcher visible anywhere. 2+ profiles → profile icon в title bar.
- **VS Code Profiles**: введён 2023; Profile concept скрыт пока не открыл `Settings → Profiles`.
- **Notion Workspaces**: 1 workspace = invisible switcher; multiple = visible at top-left.
- **Google Account on Android**: 1 account = no account switcher; 2+ = появляется.

**Применение в проекте** (current consumers):
- **Named configs** (F-014, FR-003d) — Settings → «Мои конфиги» entry hidden if `configCount == 1`. Появляется при создании второго named config. Сворачивается обратно если admin удаляет все non-default.
- **Future consumers** (TODO-FUTURE-DESIGN-PRINCIPLE — apply to):
  - Flow tabs (BottomFlowBar): 1 flow → no tab bar at bottom (single flow рендерится full-screen). 2+ flows → tab bar появляется.
  - Paired devices: 1 paired → упрощённая навигация без device picker. 2+ paired → device picker появляется.
  - Admin-managed bondings: 1 ↔ упрощённый UI. 2+ → расширенный.

**Implementation pattern**:
- Conditional rendering на основе observable `flow<Boolean> = sourceRepository.count.map { it > 1 }`.
- `derivedStateOf` в Compose layer для efficient subscription.
- НЕ persistent state «admin once saw multi-X UI» в DataStore — UI следует за current count, не за history.

**Что НЕ ставить под сомнение**:
- Не предлагать «всегда показывать switcher для discoverability» — нарушает CLAUDE.md rule 4 (MVA в presentation layer). Bloat для 80% users.
- Не предлагать «one-way transition: появился раз — навсегда» — если пользователь вернулся к single state, UI должен сворачиваться обратно. Чистый stateless rendering.
- Не предлагать tutorial overlay при first transition — только subtle toast (3 сек), не блокирующий UI.

**Reference**: [F-014 spec FR-003d](../../specs/014-tile-editing-admin-senior-profiles/spec.md), [NN/g Progressive Disclosure](https://www.nngroup.com/articles/progressive-disclosure/).

---

## Когда добавлять в этот файл

После решения, которое:
- **Зафиксировано в коде** или подробно проработано в спеке.
- Может быть повторно поднято agent'ом или ревьюером, не знающим истории.
- Имеет высокую стоимость пересмотра (one-way door).

Не добавлять:
- TODO-задачи (они в `project-backlog.md`).
- Tactical детали реализации (они в спеках).
- Vision-уровень (он в `docs/product/`).

---

## TL;DR на русском

Это **файл констант проекта** — список решений, которые **уже приняты и реализованы**, но agent'ы (и люди) забывают и переспрашивают. Перед тем как спросить пользователя «как должно работать X», agent сначала grep'ит этот файл. Сейчас здесь зафиксированы: семь-тап жест входа в admin mode (5 сек, ±48dp, пустое место home), bottom flow tab bar, Flow Templates типизация workspace'ов, разделение senior-safe правил (только для бабушкиного режима использования) vs mainstream UX (для admin и режима настройки бабушки), и кто может настраивать Simple Launcher (бабушка через 7-tap локально, admin через target editing удалённо). Не дёргать пользователя по этим темам.
